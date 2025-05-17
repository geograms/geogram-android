package offgrid.geogram.events;

import java.util.ArrayList;

public class Event {

    final EventType eventType;
    final ArrayList<EventAction> list = new ArrayList<>();

    public Event(EventType eventType) {
        this.eventType = eventType;
    }
}
