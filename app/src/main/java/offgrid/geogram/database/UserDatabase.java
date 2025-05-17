package offgrid.geogram.database;

import java.util.concurrent.CopyOnWriteArrayList;

public class UserDatabase {

    CopyOnWriteArrayList<User> users = new CopyOnWriteArrayList<>();

    public void addUser(User userToAdd){
        users.add(userToAdd);
    }

}
