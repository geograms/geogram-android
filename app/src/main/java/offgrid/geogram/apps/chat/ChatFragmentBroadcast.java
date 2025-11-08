package offgrid.geogram.apps.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.Iterator;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.api.GeogramChatAPI;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.old.databaseold.BioProfile;
import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender;
import offgrid.geogram.settings.SettingsLoader;
import offgrid.geogram.settings.SettingsUser;
import offgrid.geogram.util.DateUtils;

public class ChatFragmentBroadcast extends Fragment {

    public static String TAG = "BroadcastChatFragment";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout chatMessageContainer;
    private ScrollView chatScrollView;
    private Spinner spinnerCommunicationMode;
    private Spinner spinnerRadius;
    private String selectedCommunicationMode;
    private String selectedRadius;
    private Runnable internetMessagePoller;
    private static final long POLL_INTERVAL_MS = 30000; // 30 seconds, like HTML app
    private Location lastKnownLocation;
    private static final int DEFAULT_RADIUS_KM = 100;

    public boolean canAddMessages() {
        return isAdded()                      // Fragment is attached to Activity
                && getContext() != null       // Context is not null
                && getView() != null          // Root view is available
                && getUserVisibleHintSafe()   // Only update if user can actually see the screen
                && chatMessageContainer != null
                && chatScrollView != null;
    }

    private boolean getUserVisibleHintSafe() {
        // If you're using ViewPager, replace with isVisible() or similar
        return isVisible() && !isHidden();
    }

    public void addMessage(ChatMessage message){
        // get the id for this device
        String idThisDevice = Central.getInstance().getSettings().getIdDevice();
        if(canAddMessages()){
            // when possible, include the message on the chat window
            updateMessage(idThisDevice, message);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_broadcast_chat, container, false);

        // Initialize message input and send button
        EditText messageInput = view.findViewById(R.id.message_input);
        ImageButton btnSend = view.findViewById(R.id.btn_send);

        // Initialize chat message container and scroll view
        chatMessageContainer = view.findViewById(R.id.chat_message_container);
        chatScrollView = view.findViewById(R.id.chat_scroll_view);

        // Initialize spinners
        spinnerCommunicationMode = view.findViewById(R.id.spinner_communication_mode);
        spinnerRadius = view.findViewById(R.id.spinner_radius);

        // Load saved settings
        SettingsUser settings = Central.getInstance().getSettings();
        selectedCommunicationMode = settings.getChatCommunicationMode();
        int savedRadiusKm = settings.getChatRadiusKm();
        selectedRadius = savedRadiusKm + " km";

        // Setup communication mode spinner
        ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.communication_modes,
                R.layout.spinner_item
        );
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerCommunicationMode.setAdapter(modeAdapter);

        // Set saved selection
        int modePosition = getModePosition(selectedCommunicationMode);
        spinnerCommunicationMode.setSelection(modePosition);

        spinnerCommunicationMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCommunicationMode = parent.getItemAtPosition(position).toString();
                Log.i(TAG, "Communication mode selected: " + selectedCommunicationMode);

                // Save to settings
                SettingsUser settings = Central.getInstance().getSettings();
                settings.setChatCommunicationMode(selectedCommunicationMode);
                SettingsLoader.saveSettings(requireContext(), settings);

                // Start/stop internet polling based on mode
                handleCommunicationModeChange();

                // Refresh messages immediately
                eraseMessagesFromWindow();
                updateMessages();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Setup radius spinner
        ArrayAdapter<CharSequence> radiusAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.radius_options,
                R.layout.spinner_item
        );
        radiusAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerRadius.setAdapter(radiusAdapter);

        // Set saved radius position
        int radiusPosition = getRadiusPosition(savedRadiusKm);
        spinnerRadius.setSelection(radiusPosition);

        spinnerRadius.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedRadius = parent.getItemAtPosition(position).toString();
                Log.i(TAG, "Radius selected: " + selectedRadius);

                // Save to settings
                int radiusKm = getSelectedRadiusKm();
                SettingsUser settings = Central.getInstance().getSettings();
                settings.setChatRadiusKm(radiusKm);
                SettingsLoader.saveSettings(requireContext(), settings);

                // Refresh messages with new radius
                if (shouldFetchInternetMessages()) {
                    eraseMessagesFromWindow();
                    updateMessages();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Send button functionality
        btnSend.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if(message.isEmpty() || message.isBlank()){
                return;
            }

            // Clear input and show message immediately (optimistic UI)
            messageInput.setText("");

            // Add message to database immediately for instant display
            String callsign = Central.getInstance().getSettings().getCallsign();
            ChatMessage optimisticMessage = new ChatMessage(callsign, message);
            optimisticMessage.setWrittenByMe(true);
            optimisticMessage.setTimestamp(System.currentTimeMillis());

            // Determine message type based on mode
            if ("Internet only".equals(selectedCommunicationMode)) {
                optimisticMessage.setMessageType(ChatMessageType.INTERNET);
            } else if ("Local only".equals(selectedCommunicationMode)) {
                optimisticMessage.setMessageType(ChatMessageType.LOCAL);
            } else {
                // For "Everything" mode, default to LOCAL (will get internet copy too)
                optimisticMessage.setMessageType(ChatMessageType.LOCAL);
            }

            DatabaseMessages.getInstance().add(optimisticMessage);

            // Add message directly to UI for immediate display
            addUserMessage(optimisticMessage);
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));

            // Send in background
            new Thread(() -> {
                boolean sentLocal = false;
                boolean sentInternet = false;
                String errorMessage = null;

                // Send via Bluetooth if needed
                if ("Local only".equals(selectedCommunicationMode) || "Everything".equals(selectedCommunicationMode)) {
                    try {
                        BluetoothSender.getInstance(getContext()).sendMessage(message);
                        sentLocal = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send via Bluetooth", e);
                        errorMessage = "Bluetooth send failed: " + e.getMessage();
                    }
                }

                // Send via Internet if needed
                if ("Internet only".equals(selectedCommunicationMode) || "Everything".equals(selectedCommunicationMode)) {
                    try {
                        Location location = getLastKnownLocation();
                        if (location != null) {
                            SettingsUser userSettings = Central.getInstance().getSettings();
                            String userCallsign = userSettings.getCallsign();
                            String nsec = userSettings.getNsec();
                            String npub = userSettings.getNpub();

                            Log.d(TAG, "Sending internet message - Location: " + location.getLatitude() + "," + location.getLongitude());
                            Log.d(TAG, "Callsign: " + userCallsign);

                            if (userCallsign != null && !userCallsign.isEmpty() && nsec != null && !nsec.isEmpty() && npub != null && !npub.isEmpty()) {
                                boolean success = GeogramChatAPI.writeMessage(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        message,
                                        userCallsign,
                                        nsec,
                                        npub
                                );

                                if (success) {
                                    sentInternet = true;
                                    Log.i(TAG, "Message sent to server successfully");
                                } else {
                                    Log.e(TAG, "Server returned failure response");
                                    errorMessage = "Server rejected message";
                                }
                            } else {
                                Log.e(TAG, "User identity not configured");
                                errorMessage = "User identity not configured (callsign, nsec, or npub missing)";
                            }
                        } else {
                            Log.e(TAG, "No location available for internet send");
                            errorMessage = "No location available";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send via Internet", e);
                        errorMessage = "Internet send failed: " + e.getMessage();
                    }
                }

                final boolean anySent = sentLocal || sentInternet;
                final boolean finalSentInternet = sentInternet;
                final boolean finalSentLocal = sentLocal;
                final String finalError = errorMessage;

                requireActivity().runOnUiThread(() -> {
                    // Message already displayed optimistically, just show status
                    if (anySent) {
                        // Show success message
                        if (finalSentInternet && finalSentLocal) {
                            Toast.makeText(getContext(), "Message sent via Bluetooth and Internet", Toast.LENGTH_SHORT).show();
                        } else if (finalSentInternet) {
                            Toast.makeText(getContext(), "Message sent via Internet", Toast.LENGTH_SHORT).show();
                        } else if (finalSentLocal) {
                            Toast.makeText(getContext(), "Message sent via Bluetooth", Toast.LENGTH_SHORT).show();
                        }
                    } else if (finalError != null) {
                        // Send failed - show error
                        Toast.makeText(getContext(), finalError, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });


        // update the message right now on the chat box
        this.eraseMessagesFromWindow();
        // add all messages again
        updateMessages();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop message polling and unregister listener to avoid memory leaks
        stopInternetMessagePolling();
        handler.removeCallbacksAndMessages(null);
        BroadcastSender.removeMessageUpdateListener();
    }



    /**
     * Updates the chat message container with new messages.
     */
    private void updateMessages() {
        boolean newMessagesWereAdded = false;

        // get the list of tickets in reverse order (most recent first)
        Iterator<ChatMessage> messageIterator = DatabaseMessages.getInstance().getMessages().descendingIterator();
        if(messageIterator.hasNext() == false){
            return;
        }

        String idThisDevice = Central.getInstance().getSettings().getIdDevice();

        // iterate all messages, starting with the most recent ones
        while(messageIterator.hasNext()){
            ChatMessage message = messageIterator.next();
            updateMessage(idThisDevice, message);
//            // message types to ignore
//            // ignore position messages that start with +
//            if(message.getMessage().startsWith("+")){
//                continue;
//            }
//
//            // display the message on the screen
//            if (message.getAuthorId().equals(idThisDevice)
//                    //message.isWrittenByMe()
//            ) {
//                addUserMessage(message);
//            } else {
//                addReceivedMessage(message);
//            }
//            // inform that new messages were added
//            newMessagesWereAdded = true;
        }
       // if(newMessagesWereAdded){
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        //}
    }

    private void updateMessage(String idThisDevice, ChatMessage message) {
        // message types to ignore
        // ignore position messages that start with +
        if(message.getMessage().startsWith("+")){
            return;
        }

        // Filter messages based on communication mode
        if (!shouldDisplayMessage(message)) {
            return;
        }

        // display the message on the screen
        if (message.getAuthorId().equals(idThisDevice)
            //message.isWrittenByMe()
        ) {
            addUserMessage(message);
        } else {
            addReceivedMessage(message);
        }
    }

    /**
     * Check if message should be displayed based on communication mode
     */
    private boolean shouldDisplayMessage(ChatMessage message) {
        if (selectedCommunicationMode == null) {
            return true; // Show all if mode not set
        }

        ChatMessageType type = message.getMessageType();

        // Old messages without a type set (DATA, TEXT, CHAT, etc.) should be treated as LOCAL
        // since they came from Bluetooth before we added internet functionality
        if (type != ChatMessageType.LOCAL && type != ChatMessageType.INTERNET) {
            type = ChatMessageType.LOCAL;
        }

        switch (selectedCommunicationMode) {
            case "Local only":
                return type == ChatMessageType.LOCAL;
            case "Internet only":
                return type == ChatMessageType.INTERNET;
            case "Everything":
                return type == ChatMessageType.LOCAL || type == ChatMessageType.INTERNET;
            default:
                return true; // Show all by default
        }
    }

    /**
     * Adds a user message to the chat message container.
     *
     * @param message The message to display.
     */
    private void addUserMessage(ChatMessage message) {
        View userMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_user_message, chatMessageContainer, false);
        TextView messageTextView = userMessageView.findViewById(R.id.message_user_self);
        messageTextView.setText(message.getMessage());

        // add the other details
        TextView textBoxUpper = userMessageView.findViewById(R.id.upper_text);
        TextView textBoxLower = userMessageView.findViewById(R.id.lower_text);

        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        textBoxUpper.setText("");
        textBoxLower.setText(dateText);

        try{
            chatMessageContainer.addView(userMessageView);
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        }catch (Exception e){
            Log.e(TAG, "Failed to add message on the chat window");
        }
    }

    /**
     * Adds a received message to the chat message container.
     *
     * @param message The message to display.
     */
    private void addReceivedMessage(ChatMessage message) {
        View receivedMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_received_message, chatMessageContainer, false);

        // Get the objects
        TextView textBoxUpper = receivedMessageView.findViewById(R.id.message_boxUpper);
        TextView textBoxLower = receivedMessageView.findViewById(R.id.message_boxLower);

        BioProfile profile = null; //BioDatabase.get(message.getAuthorId(), this.getContext());
        String nickname = "";

        if (profile != null) {
            nickname = profile.getNick();
        }

        // Add the timestamp
        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        textBoxUpper.setText("");

        // Set the sender's name with origin label
        String origin = getMessageOriginLabel(message);
        if (nickname.isEmpty() && message.getAuthorId() != null) {
            textBoxLower.setText(message.getAuthorId() + " " + origin);
        } else {
            String idText = nickname + " " + origin + "    " + dateText;
            textBoxLower.setText(idText);
        }



        // Set the message content
        String text = message.getMessage();
//        if(text.startsWith(tagBio) && profile != null){
//            if(profile.getExtra() == null){
//                // add a nice one line ASCII emoticon
//                profile.setExtra(ASCII.getRandomOneliner());
//                BioDatabase.save(profile.getDeviceId(), profile, this.getContext());
//            }
//            text = profile.getExtra();
//        }
        TextView messageTextView = receivedMessageView.findViewById(R.id.message_user_1);
        messageTextView.setText(text);

        // Apply balloon style based on user's unique color
        String colorBackground = profile != null ? profile.getColor() : getUserColor(message.getAuthorId());
        applyBalloonStyle(messageTextView, colorBackground);

        // Add click listener to navigate to the user profile
        receivedMessageView.setOnClickListener(v -> {
            if (profile != null) {
                //navigateToDeviceDetails(profile);
            } else {
                Toast.makeText(getContext(), "Profile not found", Toast.LENGTH_SHORT).show();
            }
        });

        // Add the view to the container
        chatMessageContainer.addView(receivedMessageView);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * Get the origin label for a message
     * @param message The message
     * @return "(internet)" or "(bluetooth)" based on message type
     */
    private String getMessageOriginLabel(ChatMessage message) {
        ChatMessageType type = message.getMessageType();

        // Treat untagged messages as LOCAL (legacy Bluetooth messages)
        if (type != ChatMessageType.LOCAL && type != ChatMessageType.INTERNET) {
            type = ChatMessageType.LOCAL;
        }

        if (type == ChatMessageType.INTERNET) {
            return "(internet)";
        } else {
            return "(bluetooth)";
        }
    }

    /**
     * Generate a consistent color for each user based on their authorId
     * @param authorId The user's callsign/ID
     * @return A color name for the balloon background
     */
    private String getUserColor(String authorId) {
        if (authorId == null || authorId.isEmpty()) {
            return "light gray";
        }

        // Array of pleasant, distinguishable colors (avoiding red/yellow which look like warnings)
        String[] colors = {
            "light blue",
            "light green",
            "light cyan",
            "pink",
            "cyan",
            "magenta",
            "blue",
            "green",
            "brown",
            "dark gray"
        };

        // Generate consistent hash from authorId
        int hash = Math.abs(authorId.hashCode());
        int colorIndex = hash % colors.length;

        return colors[colorIndex];
    }

    @Override
    public void onResume() {
        super.onResume();

        // Show top action bar for main screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }

        // Hide the floating action button
//        FloatingActionButton btnAdd = requireActivity().findViewById(R.id.btn_add);
//        if (btnAdd != null) {
//            btnAdd.hide();
//        }

        // clear the messages to refresh the window
        eraseMessagesFromWindow();

        // update the messages, ignoring the already written ones
        updateMessages();

        // Start internet polling if needed
        handleCommunicationModeChange();

        Log.i(TAG, "onResume");
    }

    private void eraseMessagesFromWindow(){
        // clear all messages from the view
        //messageLog.clear();

        if (chatMessageContainer != null) {
            chatMessageContainer.removeAllViews();
        }
    }


//    private void navigateToDeviceDetails(BioProfile profile) {
//        DeviceDetailsFragment fragment = DeviceDetailsFragment.newInstance(profile);
//
//        // make the screen appear
//        MainActivity.activity.getSupportFragmentManager()
//                .beginTransaction()
//                .replace(R.id.main, DeviceDetailsFragment.newInstance(profile))
//                .addToBackStack(null)
//                .commit();
//
//    }


    /**
     * Handle communication mode changes
     */
    private void handleCommunicationModeChange() {
        // Stop any existing polling
        stopInternetMessagePolling();

        // Start polling if needed
        if (shouldFetchInternetMessages()) {
            startInternetMessagePolling();
        }
    }

    /**
     * Check if we should fetch messages from the internet
     */
    private boolean shouldFetchInternetMessages() {
        return "Internet only".equals(selectedCommunicationMode)
                || "Everything".equals(selectedCommunicationMode);
    }

    /**
     * Start polling for internet messages
     */
    private void startInternetMessagePolling() {
        if (internetMessagePoller != null) {
            return; // Already running
        }

        internetMessagePoller = new Runnable() {
            @Override
            public void run() {
                fetchInternetMessages();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };

        // Fetch immediately, then schedule periodic fetches
        handler.post(internetMessagePoller);
        Log.i(TAG, "Started internet message polling");
    }

    /**
     * Stop polling for internet messages
     */
    private void stopInternetMessagePolling() {
        if (internetMessagePoller != null) {
            handler.removeCallbacks(internetMessagePoller);
            internetMessagePoller = null;
            Log.i(TAG, "Stopped internet message polling");
        }
    }

    /**
     * Fetch messages from the internet API
     */
    private void fetchInternetMessages() {
        new Thread(() -> {
            try {
                // Get location
                Location location = getLastKnownLocation();
                if (location == null) {
                    Log.i(TAG, "No location available for internet messages");
                    return;
                }

                // Get user settings
                SettingsUser settings = Central.getInstance().getSettings();
                String callsign = settings.getCallsign();
                String nsec = settings.getNsec();
                String npub = settings.getNpub();

                if (callsign == null || nsec == null || npub == null) {
                    Log.i(TAG, "User identity not configured");
                    return;
                }

                // Fetch messages from API
                int radiusKm = getSelectedRadiusKm();
                List<ChatMessage> messages = GeogramChatAPI.readMessages(
                        location.getLatitude(),
                        location.getLongitude(),
                        radiusKm,
                        callsign,
                        nsec,
                        npub
                );

                // Add messages to database
                for (ChatMessage message : messages) {
                    DatabaseMessages.getInstance().add(message);
                }

                // Update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    eraseMessagesFromWindow();
                    updateMessages();
                });

                Log.i(TAG, "Fetched " + messages.size() + " messages from internet");

            } catch (Exception e) {
                Log.e(TAG, "Error fetching internet messages", e);
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(),
                                "Failed to fetch internet messages: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * Get the last known location
     */
    private Location getLastKnownLocation() {
        if (lastKnownLocation != null) {
            return lastKnownLocation;
        }

        if (getContext() == null) {
            return null;
        }

        LocationManager locationManager = (LocationManager) requireContext()
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (locationManager == null) {
            return null;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Location permission not granted");
            return null;
        }

        // Try GPS first
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            // Fallback to network provider
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        lastKnownLocation = location;
        return location;
    }

    /**
     * Get the currently selected communication mode
     * @return "Local only", "Internet only", or "Everything"
     */
    public String getSelectedCommunicationMode() {
        return selectedCommunicationMode;
    }

    /**
     * Get the currently selected radius
     * @return radius string (e.g., "10 km")
     */
    public String getSelectedRadius() {
        return selectedRadius;
    }

    /**
     * Get the radius value in kilometers
     * @return radius value as integer
     */
    public int getSelectedRadiusKm() {
        if (selectedRadius == null) {
            return DEFAULT_RADIUS_KM;
        }
        // Extract number from string like "10 km"
        String[] parts = selectedRadius.split(" ");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return DEFAULT_RADIUS_KM;
        }
    }

    /**
     * Get spinner position for communication mode
     */
    private int getModePosition(String mode) {
        if ("Local only".equals(mode)) return 0;
        if ("Internet only".equals(mode)) return 1;
        return 2; // "Everything" is default
    }

    /**
     * Get spinner position for radius
     */
    private int getRadiusPosition(int radiusKm) {
        String[] radii = getResources().getStringArray(R.array.radius_options);
        for (int i = 0; i < radii.length; i++) {
            if (radii[i].startsWith(String.valueOf(radiusKm))) {
                return i;
            }
        }
        // Default to 100 km (position might vary, find it)
        for (int i = 0; i < radii.length; i++) {
            if (radii[i].startsWith("100")) {
                return i;
            }
        }
        return 5; // fallback to index 5 (100 km in our array)
    }

    private void applyBalloonStyle(TextView messageTextView, String backgroundColor) {
        int bgColor;
        int textColor;

        // Define readable text color based on the background color
        switch (backgroundColor.toLowerCase()) {
            case "black":
                bgColor = getResources().getColor(R.color.black);
                textColor = getResources().getColor(R.color.white);
                break;
            case "yellow":
                bgColor = getResources().getColor(R.color.yellow);
                textColor = getResources().getColor(R.color.black);
                break;
            case "blue":
            case "green":
            case "cyan":
            case "red":
            case "magenta":
            case "pink":
            case "brown":
            case "dark gray":
            case "light red":
            case "white":
                bgColor = getResources().getColor(getResources().getIdentifier(backgroundColor.replace(" ", "_").toLowerCase(), "color", requireContext().getPackageName()));
                textColor = backgroundColor.equalsIgnoreCase("white") ? getResources().getColor(R.color.black) : getResources().getColor(R.color.white);
                break;
            case "light blue":
            case "light green":
            case "light cyan":
                bgColor = getResources().getColor(getResources().getIdentifier(backgroundColor.replace(" ", "_").toLowerCase(), "color", requireContext().getPackageName()));
                textColor = getResources().getColor(R.color.black);
                break;

            default:
                // Fallback to a neutral background and readable text color
                bgColor = getResources().getColor(R.color.light_gray); // Define a light gray fallback in colors.xml
                textColor = getResources().getColor(R.color.black);
                break;
        }

        // Apply the background and text colors to the message TextView
        if (messageTextView.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) messageTextView.getBackground();
            background.setColor(bgColor);
        }
        messageTextView.setTextColor(textColor);
    }


}
