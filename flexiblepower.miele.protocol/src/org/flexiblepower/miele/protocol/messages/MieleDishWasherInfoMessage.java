package org.flexiblepower.miele.protocol.messages;

/********************************************
 * Copyright (c) 2011 Alliander.            *
 * All rights reserved.                     *
 *                                          *
 * Contributors:                            *
 *     IBM - initial API and implementation *
 *     TNO - modifications for FPS          *
 *******************************************/

import java.util.Date;

/**
 * @author IBM, TNO
 * @version 0.7.0
 */
public class MieleDishWasherInfoMessage extends MieleApplianceInfoMessage {

    protected Date startTime;
    protected int remainingTime;
    protected int duration;
    protected String program;
    protected String phase;

    /**
     * Default constructor.
     */
    public MieleDishWasherInfoMessage() {
        super();
    }

    /**
     * Constructor to support downcasting. It creates a MieleDishWasherInfoMessage from a MieleApplianceInfoMessage
     * object.
     * 
     * @param a
     *            The MieleApplianceInfoMessage instance to create a new MieleDishWasherInfoMessage object.
     */
    public MieleDishWasherInfoMessage(final MieleApplianceInfoMessage a) {
        super();

        // Set attributes from parameter
        setApplianceId(a.getApplianceId());
        setApplianceType(a.getApplianceType());
        setApplianceState(a.getApplianceState());
        setApplianceClass(a.getApplianceClass());
        setActions(a.getActions());
    }

    /**
     * @return TODO
     */
    public int getDuration() {
        return duration;
    }

    /**
     * @return TODO
     */
    public String getPhase() {
        return phase;
    }

    /**
     * @return TODO
     */
    public String getProgram() {
        return program;
    }

    /**
     * @return TODO
     */
    public int getRemainingTime() {
        return remainingTime;
    }

    /**
     * @return TODO
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * @param duration
     */
    public void setDuration(final int duration) {
        this.duration = duration;
    }

    /**
     * @param phase
     */
    public void setPhase(final String phase) {
        this.phase = phase;
    }

    /**
     * @param program
     */
    public void setProgram(final String program) {
        this.program = program;
    }

    /**
     * @param remainingTime
     */
    public void setRemainingTime(final int remainingTime) {
        this.remainingTime = remainingTime;
    }

    /**
     * @param startTime
     */
    public void setStartTime(final Date startTime) {
        this.startTime = startTime;
    }

    @Override
    public String toString() {
        return "MieleDishWasherInfoMessage [startTime=" + startTime
               + ", remainingTime="
               + remainingTime
               + ", duration="
               + duration
               + ", program="
               + program
               + ", phase="
               + phase
               + "]";
    }
}
