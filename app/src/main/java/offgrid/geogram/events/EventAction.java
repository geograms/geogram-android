package offgrid.geogram.events;

/**
 * Action to perform when a given event happens
 */
public abstract class EventAction {

    final String id;

    public abstract void action(Object... data);

    public EventAction(String id) {
        this.id = id;
    }
}
