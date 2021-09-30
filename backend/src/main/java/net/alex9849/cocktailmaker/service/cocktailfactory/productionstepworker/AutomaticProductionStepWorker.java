package net.alex9849.cocktailmaker.service.cocktailfactory.productionstepworker;

import com.pi4j.io.gpio.RaspiPin;
import net.alex9849.cocktailmaker.iface.IGpioController;
import net.alex9849.cocktailmaker.model.Pump;
import net.alex9849.cocktailmaker.model.recipe.AutomatedIngredient;
import net.alex9849.cocktailmaker.model.recipe.RecipeIngredient;
import net.alex9849.cocktailmaker.service.cocktailfactory.PumpPhase;
import net.alex9849.cocktailmaker.service.cocktailfactory.PumpTimingStepCalculator;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutomaticProductionStepWorker extends AbstractProductionStepWorker {
    private final Map<Long, Pump> pumpsByIngredientId;
    private final List<RecipeIngredient> productionStepInstructions;
    private final Map<Pump, List<PumpPhase>> pumpPumpPhases;
    private final long requiredWorkingTime;
    private final ScheduledExecutorService scheduler;
    private IGpioController gpioController;
    private Set<ScheduledFuture> scheduledPumpFutures;
    private ScheduledFuture finishTask;
    private ScheduledFuture nofierTask;

    private long startTime;
    private long endTime;
    private boolean started;

    public AutomaticProductionStepWorker(Set<Pump> pumps, IGpioController gpioController, List<RecipeIngredient> productionStepInstructions, int minimalPumpTime, int minimalBreakTime) {
        this.pumpsByIngredientId = pumps.stream().collect(Collectors
                .toMap(x -> x.getCurrentIngredient().getId(), x -> x, (x1, x2) -> x1));
        this.productionStepInstructions = productionStepInstructions;

        Map<Pump, Integer> pumpRuntimes = new HashMap<>();
        for (RecipeIngredient instruction : this.productionStepInstructions) {
            if (!(instruction.getIngredient() instanceof AutomatedIngredient)) {
                throw new IllegalArgumentException("Can't automatically fulfill productionstep. One or more given ingredients can only be added manually!");
            }
            AutomatedIngredient currAutoIngredient = (AutomatedIngredient) instruction.getIngredient();
            Pump pumpWithIngredient = this.pumpsByIngredientId.get(currAutoIngredient.getId());
            if (pumpWithIngredient == null) {
                throw new IllegalArgumentException("Can't automatically fulfill productionstep. One or more required ingredients are not assigned to pumps!");
            }
            pumpRuntimes.put(pumpWithIngredient, (int) (currAutoIngredient.getPumpTimeMultiplier() * instruction.getAmount() * pumpWithIngredient.getTimePerClInMs() / 10d));
        }
        PumpTimingStepCalculator pumpTimingStepCalculator = new PumpTimingStepCalculator(pumpRuntimes, minimalPumpTime, minimalBreakTime);
        this.pumpPumpPhases = pumpTimingStepCalculator.getPumpPhases();
        this.requiredWorkingTime = pumpTimingStepCalculator.getLongestIngredientTime();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.gpioController = gpioController;
        this.scheduledPumpFutures = new HashSet<>();
        this.started = false;
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("ProductionStepWorker has already been started!");
        }
        this.started = true;
        this.startTime = System.currentTimeMillis();
        for (Map.Entry<Pump, List<PumpPhase>> currPumpWithTimings : this.pumpPumpPhases.entrySet()) {
            for (PumpPhase pumpPhase : currPumpWithTimings.getValue()) {
                scheduledPumpFutures.add(scheduler.schedule(() -> {
                    gpioController.provideGpioPin(RaspiPin.getPinByAddress(pumpPhase.getPump().getGpioPin())).setLow();
                }, pumpPhase.getStartTime(), TimeUnit.MILLISECONDS));
                scheduledPumpFutures.add(scheduler.schedule(() -> {
                    gpioController.provideGpioPin(RaspiPin.getPinByAddress(pumpPhase.getPump().getGpioPin())).setHigh();
                }, pumpPhase.getStopTime(), TimeUnit.MILLISECONDS));
            }
        }
        long delayLastInstruction = this.scheduledPumpFutures.stream()
                .mapToLong(x -> x.getDelay(TimeUnit.MILLISECONDS)).max().getAsLong();

        this.finishTask = scheduler.schedule(this::onFinish, delayLastInstruction, TimeUnit.MILLISECONDS);
        this.nofierTask = this.scheduler.scheduleAtFixedRate(this::notifySubscribers, 1, 1, TimeUnit.SECONDS);
        this.endTime = System.currentTimeMillis() + delayLastInstruction;
        this.notifySubscribers();
    }

    private void onFinish() {
        this.scheduledPumpFutures.forEach(x -> x.cancel(false));
        this.nofierTask.cancel(false);
        this.setFinished();
    }

    public boolean isStarted() {
        return started;
    }

    public void cancel() {
        for (ScheduledFuture future : this.scheduledPumpFutures) {
            future.cancel(true);
        }
        this.finishTask.cancel(false);
        for (Pump pump : this.pumpPumpPhases.keySet()) {
            gpioController.provideGpioPin(RaspiPin.getPinByAddress(pump.getGpioPin())).setHigh();
        }
        if (this.scheduler.isShutdown()) {
            return;
        }
        this.scheduler.shutdown();
    }

    public StepProgress getProgress() {
        StepProgress stepProgress = new StepProgress();
        if(this.started) {
            stepProgress.setPercentCompleted(Math.min(100, (int) (((System.currentTimeMillis() - this.startTime) / ((double) this.endTime)) * 100)));
        } else {
            stepProgress.setPercentCompleted(0);
        }
        stepProgress.setStarted(this.started);
        stepProgress.setFinished(this.isFinished());
        return stepProgress;
    }

    public int getAmountToFill() {
        return this.productionStepInstructions.stream()
                .mapToInt(x -> x.getAmount()).sum();
    }

    public long getRequiredPumpingTime() {
        return this.requiredWorkingTime;
    }

    public Mode getMode() {
        return Mode.AUTOMATIC;
    }
}