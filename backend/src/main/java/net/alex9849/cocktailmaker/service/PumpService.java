package net.alex9849.cocktailmaker.service;

import net.alex9849.cocktailmaker.iface.IGpioController;
import net.alex9849.cocktailmaker.model.FeasibilityReport;
import net.alex9849.cocktailmaker.model.Pump;
import net.alex9849.cocktailmaker.model.cocktail.Cocktailprogress;
import net.alex9849.cocktailmaker.model.recipe.AutomatedIngredient;
import net.alex9849.cocktailmaker.model.recipe.Ingredient;
import net.alex9849.cocktailmaker.model.recipe.Recipe;
import net.alex9849.cocktailmaker.model.user.User;
import net.alex9849.cocktailmaker.payload.dto.pump.PumpDto;
import net.alex9849.cocktailmaker.repository.PumpRepository;
import net.alex9849.cocktailmaker.service.cocktailfactory.CocktailFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class PumpService {

    private static PumpService instance;

    private CocktailFactory cocktailFactory;

    @Autowired
    private PumpRepository pumpRepository;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private IGpioController gpioController;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<Long> cleaningPumpIds = new ConcurrentSkipListSet<>();

    @PostConstruct
    public void init() {
        PumpService.instance = this;
        List<Pump> pumps = getAllPumps();
        //Turn off all pumps
        pumps.forEach(pump -> {
            gpioController.getGpioPin(pump.getBcmPin()).setHigh();
        });
    }

    public static PumpService getInstance() {
        return instance;
    }

    public List<Pump> getAllPumps() {
        return pumpRepository.findAll();
    }

    public Pump getPump(long id) {
        return pumpRepository.findById(id).orElse(null);
    }

    public Pump createPump(Pump pump) {
        if(pumpRepository.findByBcmPin(pump.getBcmPin()).isPresent()) {
            throw new IllegalArgumentException("GPOI-Pin already in use!");
        }
        pump = pumpRepository.create(pump);
        //Turn off pump
        gpioController.getGpioPin(pump.getBcmPin()).setHigh();
        webSocketService.broadcastPumpLayout(getAllPumps());
        return pump;
    }

    public Pump updatePump(Pump pump) {
        Optional<Pump> beforeUpdate = pumpRepository.findById(pump.getId());
        if(!beforeUpdate.isPresent()) {
            throw new IllegalArgumentException("Pump doesn't exist!");
        }
        Optional<Pump> optPumpWithGpio = pumpRepository.findByBcmPin(pump.getBcmPin());
        if(optPumpWithGpio.isPresent()) {
            if(optPumpWithGpio.get().getId() != pump.getId()) {
                throw new IllegalArgumentException("GPOI-Pin already in use!");
            }
        }
        pumpRepository.update(pump);
        if(beforeUpdate.get().getBcmPin() != pump.getBcmPin()) {
            gpioController.getGpioPin(beforeUpdate.get().getBcmPin()).setHigh();
            gpioController.getGpioPin(pump.getBcmPin()).setHigh();
        }
        webSocketService.broadcastPumpLayout(getAllPumps());
        return pump;
    }

    public static Pump fromDto(PumpDto pumpDto) {
        if(pumpDto == null) {
            return null;
        }
        Pump pump = new Pump();
        BeanUtils.copyProperties(pumpDto, pump);
        pump.setCurrentIngredient(IngredientService.fromDto(pumpDto.getCurrentIngredient()));
        if(pump.getCurrentIngredient() != null) {
            pump.setCurrentIngredientId(pump.getCurrentIngredient().getId());
        }
        return pump;
    }

    public void deletePump(long id) {
        Pump pump = getPump(id);
        if(pump == null) {
            throw new IllegalArgumentException("Pump doesn't exist!");
        }
        pumpRepository.delete(id);
        //Turn off pump
        gpioController.getGpioPin(pump.getBcmPin()).setHigh();
        webSocketService.broadcastPumpLayout(getAllPumps());
    }

    public synchronized void orderCocktail(User user, Recipe recipe, int amount) {
        if(this.isMakingCocktail()) {
            throw new IllegalArgumentException("A cocktail is already being fabricated!");
        }
        if(isAnyCleaning()) {
            throw new IllegalStateException("There are pumps getting cleaned currently!");
        }
        CocktailFactory.transformToAmountOfLiquid(recipe, amount);
        FeasibilityReport report = this.checkFeasibility(recipe);
        if(!report.getInsufficientIngredients().isEmpty()) {
            throw new IllegalArgumentException("Some pumps don't have enough liquids left!");
        }
        this.cocktailFactory = new CocktailFactory(recipe, user, new HashSet<>(getAllPumps()), gpioController)
                .subscribeProgress(progress -> {
                    if(progress.getState() == Cocktailprogress.State.CANCELLED || progress.getState() == Cocktailprogress.State.FINISHED) {
                        this.scheduler.schedule(() -> {
                            this.cocktailFactory = null;
                            this.webSocketService.broadcastCurrentCocktail(null);
                        }, 5000, TimeUnit.MILLISECONDS);
                    }
                    this.webSocketService.broadcastCurrentCocktail(progress);
                });
        for(Pump pump : this.cocktailFactory.getUsedPumps()) {
            this.pumpRepository.update(pump);
        }
        webSocketService.broadcastPumpLayout(getAllPumps());
        this.cocktailFactory.makeCocktail();
    }

    public FeasibilityReport checkFeasibility(Recipe recipe, int amount) {
        return checkFeasibility(CocktailFactory.transformToAmountOfLiquid(recipe, amount));
    }
    public FeasibilityReport checkFeasibility(Recipe recipe) {
        Map<Ingredient, Integer> neededAmountPerIngredientId = CocktailFactory.getNeededAmountNeededPerIngredient(recipe);
        Map<Long, List<Pump>> pumpsByIngredientId = getAllPumps().stream().filter(x -> x.getCurrentIngredient() != null)
                .collect(Collectors.groupingBy(Pump::getCurrentIngredientId));
        List<FeasibilityReport.InsufficientIngredient> insufficientIngredients = new ArrayList<>();

        for(Ingredient ingredient : neededAmountPerIngredientId.keySet()) {
            int remainingNeededAmount = neededAmountPerIngredientId.get(ingredient);
            if(!pumpsByIngredientId.containsKey(ingredient.getId())) {
                //We only check for automated ingredients
                continue;
            }
            for(Pump pump : pumpsByIngredientId.get(ingredient.getId())) {
                if(remainingNeededAmount > pump.getFillingLevelInMl()) {
                    remainingNeededAmount -= pump.getFillingLevelInMl();
                } else {
                    remainingNeededAmount = 0;
                    continue;
                }
            }
            if(remainingNeededAmount > 0) {
                FeasibilityReport.InsufficientIngredient insufficientIngredient = new FeasibilityReport.InsufficientIngredient();
                insufficientIngredient.setIngredient(ingredient);
                insufficientIngredient.setAmountRemaining(remainingNeededAmount);
                insufficientIngredient.setAmountNeeded(neededAmountPerIngredientId.get(ingredient));
                insufficientIngredients.add(insufficientIngredient);
            }
        }

        FeasibilityReport report = new FeasibilityReport();
        report.setInsufficientIngredients(insufficientIngredients);
        return report;
    }

    public synchronized void continueCocktailProduction() {
        if (this.cocktailFactory == null || this.cocktailFactory.isFinished()) {
            throw new IllegalStateException("No cocktail is being prepared currently!");
        }
        this.cocktailFactory.continueProduction();
    }

    public synchronized boolean isMakingCocktail() {
        return this.cocktailFactory != null;
    }

    public synchronized boolean cancelCocktailOrder() {
        if(this.cocktailFactory == null || this.cocktailFactory.isFinished()) {
            return false;
        }
        this.cocktailFactory.cancelCocktail();
        return true;
    }

    public Cocktailprogress getCurrentCocktailProgress() {
        if(this.cocktailFactory == null) {
            return null;
        }
        return this.cocktailFactory.getCocktailprogress();
    }

    public synchronized void cleanPump(Pump pump) {
        if(isCleaning(pump)) {
            throw new IllegalArgumentException("Pump is already cleaning!");
        }
        double multiplier = 1.0;
        if(pump.getCurrentIngredient() != null) {
            multiplier = ((AutomatedIngredient) pump.getCurrentIngredient()).getPumpTimeMultiplier();
        }
        int runTime = (int) (pump.getTimePerClInMs() * multiplier / 10d) * pump.getTubeCapacityInMl();
        if (this.isMakingCocktail()) {
            throw new IllegalStateException("Can't clean pump! A cocktail is currently being made!");
        }
        this.cleaningPumpIds.add(pump.getId());
        gpioController.getGpioPin(pump.getBcmPin()).setLow();
        webSocketService.broadcastPumpLayout(getAllPumps());
        scheduler.schedule(() -> {
            gpioController.getGpioPin(pump.getBcmPin()).setHigh();
            this.cleaningPumpIds.remove(pump.getId());
            webSocketService.broadcastPumpLayout(getAllPumps());
        }, runTime, TimeUnit.MILLISECONDS);
    }

    public boolean isCleaning(Pump pump) {
        return cleaningPumpIds.contains(pump.getId());
    }

    public boolean isAnyCleaning() {
        return !cleaningPumpIds.isEmpty();
    }
}
