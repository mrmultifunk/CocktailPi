package net.alex9849.cocktailmaker.model.pump.motortasks;

import net.alex9849.cocktailmaker.model.pump.JobMetrics;
import net.alex9849.cocktailmaker.model.pump.PumpJobState;
import net.alex9849.cocktailmaker.model.pump.StepperPump;
import net.alex9849.motorlib.AcceleratingStepper;
import net.alex9849.motorlib.Direction;

import java.util.Optional;
import java.util.function.Consumer;

public class StepperMotorTask extends PumpTask {
    StepperPump stepperPump;
    long stepsToRun;
    long stepsMade;


    /**
     * @param stepsToRun Long.MAX_VALUE == unlimited
     */
    public StepperMotorTask(Long prevJobId, StepperPump stepperPump, Direction direction, long stepsToRun, Consumer<Optional<PumpJobState.RunningState>> callback) {
        super(prevJobId, stepperPump, stepsToRun == Long.MAX_VALUE, direction, callback);
        this.stepperPump = stepperPump;
        this.stepsToRun = stepsToRun;
    }

    public long getMlPumped() {
        return (stepsMade * 10) / stepperPump.getStepsPerCl();
    }

    public long getStepsMade() {
        return stepsMade;
    }

    @Override
    protected void pumpRun() {
        AcceleratingStepper driver = stepperPump.getMotorDriver();
        if (isRunInfinity()) {
            //Pick a very large number
            stepsToRun = 10000000000L;
        } else if (stepsToRun == 0) {
            stepsToRun = 1;
        }
        if (getDirection() == Direction.BACKWARD) {
            stepsToRun = -stepsToRun;
        }
        driver.move(stepsToRun);
        while (driver.distanceToGo() != 0) {
            if(driver.run()) {
                stepsMade++;
            }
            if (Thread.interrupted()) {
                return;
            }
            Thread.yield();
        }
    }

    @Override
    protected PumpJobState.RunningState genRunningState() {
        PumpJobState.RunningState runningState = new PumpJobState.RunningState();
        runningState.setPercentage((int) (stepsMade * 100 / stepsToRun));
        runningState.setForward(getDirection() == Direction.FORWARD);
        runningState.setRunInfinity(isRunInfinity());
        runningState.setJobId(getJobId());
        return runningState;
    }

    @Override
    protected JobMetrics genJobMetrics() {
        JobMetrics metrics = new JobMetrics();
        metrics.setId(getJobId());
        metrics.setMlPumped(getMlPumped());
        metrics.setStartTime(getStartTime());
        metrics.setStopTime(getStopTime());
        metrics.setStepsMade(getStepsMade());
        return metrics;
    }
}