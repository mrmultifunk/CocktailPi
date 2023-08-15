package net.alex9849.cocktailmaker.service.pumps.cocktailfactory.productionstepworker;

import net.alex9849.cocktailmaker.model.pump.Pump;
import net.alex9849.cocktailmaker.model.pump.StepperPump;
import net.alex9849.cocktailmaker.service.pumps.cocktailfactory.PumpPhase;
import net.alex9849.motorlib.motor.AcceleratingStepper;
import net.alex9849.motorlib.motor.MultiStepper;

import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractPumpingProductionStepWorker extends AbstractProductionStepWorker {
    private final ScheduledExecutorService scheduler;
    private Thread runner;
    private Set<PumpPhase> pumpPhases;
    private Map<StepperPump, Long> steppersToSteps;
    private Set<Pump> usedPumps;
    private final Set<ScheduledFuture<?>> scheduledPumpFutures;
    private ScheduledFuture<?> notifierTask;

    private int requiredWorkTime;
    private long startTime;
    private long endTime;

    public AbstractPumpingProductionStepWorker() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.requiredWorkTime = 0;
        this.usedPumps = new HashSet<>();
        this.pumpPhases = new HashSet<>();
        this.steppersToSteps = new HashMap<>();
        this.scheduledPumpFutures = new HashSet<>();
    }

    protected synchronized void setDcPumpPhases(Set<PumpPhase> pumpPhases) {
        Objects.requireNonNull(pumpPhases);
        if(this.isStarted()) {
            throw new IllegalStateException("Worker already started!");
        }
        this.pumpPhases = pumpPhases;
        this.requiredWorkTime = Math.max(this.requiredWorkTime,
                this.pumpPhases.stream().mapToInt(PumpPhase::getStopTime).max().orElse(0));
        this.usedPumps = new HashSet<>();
        for(PumpPhase pumpPhase : this.pumpPhases) {
            this.usedPumps.add(pumpPhase.getPump());
        }
    }

    protected synchronized void setSteppersToComplete(Map<StepperPump, Long> steppersToSteps) {
        Objects.requireNonNull(steppersToSteps);
        if(this.isStarted()) {
            throw new IllegalStateException("Worker already started!");
        }
        for(Map.Entry<StepperPump, Long> entry : steppersToSteps.entrySet()) {
            AcceleratingStepper driver = entry.getKey().getMotorDriver();
            long cPos = driver.getCurrentPosition();
            long cTarget = driver.getTargetPosition();
            driver.setCurrentPosition(0);
            driver.moveTo(entry.getValue());
            this.requiredWorkTime = Math.max(this.requiredWorkTime, (int) driver.estimateTimeTillCompletion());
            driver.setCurrentPosition(cPos);
            driver.moveTo(cTarget);
        }
        this.steppersToSteps.putAll(steppersToSteps);
        this.usedPumps.addAll(steppersToSteps.keySet());
    }
    protected Set<PumpPhase> getDcPumpPhases() {
        return pumpPhases;
    }

    @Override
    public synchronized void start() {
        super.start();
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + this.getRequiredPumpingTime();
        CountDownLatch cl = new CountDownLatch(this.pumpPhases.size());

        for (PumpPhase pumpPhase : this.pumpPhases) {
            scheduledPumpFutures.add(scheduler.schedule(() -> {
                pumpPhase.getPump().getMotorDriver().setRunning(true);
                pumpPhase.setStarted();
            }, pumpPhase.getStartTime(), TimeUnit.MILLISECONDS));

            scheduledPumpFutures.add(scheduler.schedule(() -> {
                pumpPhase.getPump().getMotorDriver().setRunning(false);
                pumpPhase.setStopped();
                cl.countDown();
            }, pumpPhase.getStopTime(), TimeUnit.MILLISECONDS));
        }

        this.notifierTask = this.scheduler.scheduleAtFixedRate(this::notifySubscribers, 1, 1, TimeUnit.SECONDS);
        Runnable runTask = () -> {
            MultiStepper multiStepper = new MultiStepper();
            for(Map.Entry<StepperPump, Long> entry : steppersToSteps.entrySet()) {
                AcceleratingStepper driver = entry.getKey().getMotorDriver();
                driver.move(entry.getValue());
                multiStepper.addStepper(driver);
            }
            while (multiStepper.runRound()) {
                if(Thread.interrupted()) {
                    return;
                }
                Thread.yield();
            }
            try {
                cl.await();
            } catch (InterruptedException e) {
                return;
            }
            onFinish();
        };
        runner = new Thread(runTask);
        runner.start();

        this.notifySubscribers();
    }

    @Override
    public synchronized boolean cancel() {
        if(!super.cancel()) {
            return false;
        }
        for (ScheduledFuture<?> future : this.scheduledPumpFutures) {
            future.cancel(true);
        }
        if(this.runner != null) {
            try {
                this.runner.interrupt();
                this.runner.join();
            } catch (InterruptedException e) {
                //Ignore
            }
        }
        if(this.notifierTask != null) {
            this.notifierTask.cancel(false);
        }
        this.stopAllPumps();
        if (!this.scheduler.isShutdown()) {
            this.scheduler.shutdown();
        }
        return true;
    }

    @Override
    public StepProgress getProgress() {
        StepProgress progress = new StepProgress();
        if(this.isStarted()) {
            progress.setPercentCompleted(Math.min(100, (int) (((System.currentTimeMillis() - this.startTime) / ((double) (Math.max(1, this.endTime - this.startTime)))) * 100)));
        } else {
            progress.setPercentCompleted(0);
        }
        progress.setFinished(this.isFinished());
        return progress;
    }

    private synchronized void stopAllPumps() {
        for(Pump pump : this.usedPumps) {
            pump.getMotorDriver().shutdown();
        }
    }

    protected void onFinish() {
        this.scheduledPumpFutures.forEach(x -> x.cancel(true));
        this.notifierTask.cancel(false);
        this.stopAllPumps();
        this.setFinished();
    }

    public long getRequiredPumpingTime() {
        return this.requiredWorkTime;
    }

    public Map<Pump, Integer> getNotUsedLiquid() {
        if(this.isFinished()) {
            return new HashMap<>();
        }

        Map<Pump, Double> notUsedLiquidByPumpPrecise = new HashMap<>();
        for(PumpPhase pumpPhase : this.getDcPumpPhases()) {
            double notUsedLiquid = notUsedLiquidByPumpPrecise.computeIfAbsent(pumpPhase.getPump(), p -> 0d);
            notUsedLiquid += pumpPhase.getRemainingLiquidToPump();
            notUsedLiquidByPumpPrecise.put(pumpPhase.getPump(), notUsedLiquid);
        }
        if(this.isStarted()) {
            for(StepperPump stepperPump : this.steppersToSteps.keySet()) {
                double notUsedLiquid = (double) (stepperPump.getMotorDriver().distanceToGo() * 10) / stepperPump.getStepsPerCl();
                notUsedLiquidByPumpPrecise.put(stepperPump, notUsedLiquid);
            }
        } else {
            for(Map.Entry<StepperPump, Long> entry : this.steppersToSteps.entrySet()) {
                notUsedLiquidByPumpPrecise.put(entry.getKey(), entry.getValue().doubleValue());
            }
        }

        Map<Pump, Integer> notUsedLiquidByPump = new HashMap<>();
        notUsedLiquidByPumpPrecise.forEach((key, value) -> {
            notUsedLiquidByPump.put(key, (int) Math.round(value));
        });
        return notUsedLiquidByPump;
    }

    public Set<Pump> getUsedPumps() {
        return usedPumps;
    }
}
