package org.rmatil.sync.core.syncer.file.event;

import org.rmatil.sync.event.aggregator.core.events.AEvent;

public class TerminateSyncerEvent extends AEvent {

    private static final long serialVersionUID = 2509624936040618134L;

    public static final String EVENT_NAME = "event.terminate";

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }
}
