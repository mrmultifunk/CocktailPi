package net.alex9849.cocktailmaker.model.recipe;

import net.alex9849.cocktailmaker.model.FeasibilityReport;
import net.alex9849.cocktailmaker.model.Pump;
import net.alex9849.cocktailmaker.model.recipe.ingredient.AddableIngredient;
import net.alex9849.cocktailmaker.model.recipe.ingredient.Ingredient;
import net.alex9849.cocktailmaker.model.recipe.ingredient.IngredientGroup;
import net.alex9849.cocktailmaker.model.recipe.productionstep.AddIngredientsProductionStep;
import net.alex9849.cocktailmaker.model.recipe.productionstep.ProductionStep;
import net.alex9849.cocktailmaker.model.recipe.productionstep.ProductionStepIngredient;
import net.alex9849.cocktailmaker.service.cocktailfactory.CocktailFactory;

import java.util.*;
import java.util.stream.Collectors;

public class FeasibilityFactory {
    private final Recipe recipe;
    private final IngredientGroupReplacements replacements;
    private final List<Pump> pumps;
    private final FeasibilityReport feasibilityReport;
    private final FeasibleRecipe feasibleRecipe;

    /**
     * @param recipe The recipe that should be checked. Note: IngredientGroups within this instance will be replaced. Don't continue to use this object
     */
    public FeasibilityFactory(Recipe recipe, IngredientGroupReplacements replacements, List<Pump> pumps) {
        this.recipe = recipe;
        this.replacements = replacements;
        this.pumps = pumps;
        this.feasibilityReport = new FeasibilityReport();
        this.feasibleRecipe = new FeasibleRecipe();
        this.feasibleRecipe.setRecipe(recipe);
        this.compute();
    }

    private void compute() {
        this.computeIngredientGroupReplacementsAndFeasibleRecipe();
        this.computeInsufficientIngredients();
        this.computeIngredientsToAddManually();
    }

    private void computeIngredientGroupReplacementsAndFeasibleRecipe() {
        Map<Long, List<IngredientGroup>> missingIngredientGroupReplacements = new HashMap<>();
        List<ProductionStep> feasibleProductionSteps = new ArrayList<>();
        for (ProductionStep productionStep : recipe.getProductionSteps()) {
            if (!(productionStep instanceof AddIngredientsProductionStep)) {
                feasibleProductionSteps.add(productionStep);
                continue;
            }
            AddIngredientsProductionStep aipStep = (AddIngredientsProductionStep) productionStep;
            AddIngredientsProductionStep feasibleAddIngredientsProductionStep = new AddIngredientsProductionStep();
            feasibleProductionSteps.add(feasibleAddIngredientsProductionStep);

            List<ProductionStepIngredient> feasibleProductionStepIngredients = new ArrayList<>();
            feasibleAddIngredientsProductionStep.setStepIngredients(feasibleProductionStepIngredients);

            for (int i = 0; i < aipStep.getStepIngredients().size(); i++) {
                ProductionStepIngredient psIngredient = aipStep.getStepIngredients().get(i);
                ProductionStepIngredient feasibleProductionStepIngredient = new ProductionStepIngredient();
                feasibleProductionStepIngredient.setAmount(psIngredient.getAmount());
                feasibleProductionStepIngredient.setScale(psIngredient.isScale());
                feasibleProductionStepIngredients.add(feasibleProductionStepIngredient);

                //We only replace IngredientGroups
                if (!(psIngredient.getIngredient() instanceof IngredientGroup)) {
                    feasibleProductionStepIngredient.setIngredient(psIngredient.getIngredient());
                    continue;
                }

                IngredientGroup toReplaceIngredientGroup = (IngredientGroup) psIngredient.getIngredient();
                AddableIngredient addableIngredient = replacements.getReplacement(i, psIngredient.getIngredient().getId());
                if (addableIngredient == null) {
                    List<IngredientGroup> missingIngredientGroups = missingIngredientGroupReplacements
                            .computeIfAbsent((long) i, (x) -> new ArrayList<>());
                    missingIngredientGroups.add(toReplaceIngredientGroup);
                    feasibleProductionStepIngredient.setIngredient(toReplaceIngredientGroup);
                } else {
                    if(toReplaceIngredientGroup.getAddableIngredientChildren().stream()
                            .anyMatch(x -> x.getId() == addableIngredient.getId())) {
                        feasibleProductionStepIngredient.setIngredient(addableIngredient);
                    } else {
                        throw new IllegalArgumentException(toReplaceIngredientGroup.getName()
                                + " can't be replaced with " + addableIngredient.getName());
                    }
                }
            }
        }
        this.feasibleRecipe.setFeasibleProductionSteps(feasibleProductionSteps);
        this.feasibilityReport.setMissingIngredientGroupReplacements(missingIngredientGroupReplacements);
    }

    private void computeInsufficientIngredients() {
        Map<Ingredient, Integer> neededAmountPerIngredientId = CocktailFactory.getNeededAmountNeededPerIngredient(recipe);
        Map<Long, List<Pump>> pumpsByIngredientId = this.pumps.stream().filter(x -> x.getCurrentIngredient() != null)
                .collect(Collectors.groupingBy(Pump::getCurrentIngredientId));
        List<FeasibilityReport.InsufficientIngredient> insufficientIngredients = new ArrayList<>();

        for (Ingredient ingredient : neededAmountPerIngredientId.keySet()) {
            int remainingNeededAmount = neededAmountPerIngredientId.get(ingredient);
            if (!pumpsByIngredientId.containsKey(ingredient.getId())) {
                //We only check for automated ingredients
                continue;
            }
            for (Pump pump : pumpsByIngredientId.get(ingredient.getId())) {
                if (remainingNeededAmount > pump.getFillingLevelInMl()) {
                    remainingNeededAmount -= pump.getFillingLevelInMl();
                } else {
                    remainingNeededAmount = 0;
                }
            }
            if (remainingNeededAmount > 0) {
                FeasibilityReport.InsufficientIngredient insufficientIngredient = new FeasibilityReport.InsufficientIngredient();
                insufficientIngredient.setIngredient(ingredient);
                insufficientIngredient.setAmountRemaining(remainingNeededAmount);
                insufficientIngredient.setAmountNeeded(neededAmountPerIngredientId.get(ingredient));
                insufficientIngredients.add(insufficientIngredient);
            }
        }
        this.feasibilityReport.setInsufficientIngredients(insufficientIngredients);
    }

    private void computeIngredientsToAddManually() {
        Set<Ingredient> ingredientsToAddManually = new HashSet<>();
        for(ProductionStep pStep : this.feasibleRecipe.getFeasibleProductionSteps()) {
            if(!(pStep instanceof AddIngredientsProductionStep)) {
                continue;
            }
            AddIngredientsProductionStep aiPStep = (AddIngredientsProductionStep) pStep;
            for(ProductionStepIngredient pStepIngredient : aiPStep.getStepIngredients()) {
                if(!pStepIngredient.getIngredient().isOnPump()) {
                    ingredientsToAddManually.add(pStepIngredient.getIngredient());
                }
            }
        }
        this.feasibilityReport.setIngredientsToAddManually(ingredientsToAddManually);
    }

    public FeasibilityReport getFeasibilityReport() {
        return this.feasibilityReport;
    }

    public FeasibleRecipe getFeasibleRecipe() {
        if (!this.getFeasibilityReport().isFeasible()) {
            return null;
        }
        return this.feasibleRecipe;
    }
}