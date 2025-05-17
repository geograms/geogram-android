package offgrid.geogram.events;


import java.util.HashMap;

public class EventControl {

    private static final HashMap<EventType, Event> list = new HashMap<>();

    public static void addEvent(EventType eventType, EventAction action) {
        Event event = null;
        if (list.containsKey(eventType)) {
            event = list.get(eventType);
        }else{
            event = new Event(eventType);
            list.put(eventType, event);
        }
        if(event == null){
            return;
        }
        // avoid duplicates
        for(EventAction actionAdded : event.list){
            if(actionAdded.id.equals(action.id)){
                return;
            }
        }
        // can add the new action
        event.list.add(action);
    }

    /**
     * Starts the actions related to a specific event
     * @param eventType the type of event to be started
     * @param data the data we want to pass onto the actions
     */
    public static void startEvent(EventType eventType, Object... data){
        Event event = list.get(eventType);
        if(event == null){
            return;
        }
        for(EventAction action : event.list){
            action.action(data);
        }
    }

}
