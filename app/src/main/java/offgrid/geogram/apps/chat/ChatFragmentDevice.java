package offgrid.geogram.apps.chat;

// Removed (legacy Google Play Services code) - import static offgrid.geogram.old.bluetooth_old.other.comms.BlueCommands.tagBio;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.R;
import offgrid.geogram.core.Log;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.databaseold.BioDatabase;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.databaseold.BioProfile;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;
import offgrid.geogram.util.ASCII;
import offgrid.geogram.util.DateUtils;

public class ChatFragmentDevice extends Fragment {

    private static final String ARG_DEVICE_ID = "device_id";
    private static final String TAG = "DeviceChatFragment";
    private String deviceId;
    private LinearLayout chatMessageContainer;
    private ScrollView chatScrollView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Removed - BioProfile was part of old code
    // BioProfile profile = null;

    public static ChatFragmentDevice newInstance(String deviceId) {
        ChatFragmentDevice fragment = new ChatFragmentDevice();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_chat, container, false);

        if (getArguments() != null) {
            deviceId = getArguments().getString(ARG_DEVICE_ID);
        }

        if (deviceId == null) {
            Toast.makeText(getContext(), "Device Id is null", Toast.LENGTH_SHORT).show();
            requireActivity().onBackPressed();
        }

        // Removed - BioDatabase was part of old code
        // BioProfile profile = BioDatabase.get(deviceId, this.getContext());
        String nickname = deviceId; // Use device ID as nickname

        // if (profile != null) {
        //     nickname = profile.getNick();
        // }

        Log.i("DeviceChatFragment", "Chatting with device: " + deviceId);

        // Initialize UI
        EditText messageInput = view.findViewById(R.id.message_input);
        //ImageButton btnSend = view.findViewById(R.id.btn_send);
        chatMessageContainer = view.findViewById(R.id.chat_message_container);
        chatScrollView = view.findViewById(R.id.chat_scroll_view);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // change the title
        TextView chatTitleTextView = view.findViewById(R.id.chat_title);
        chatTitleTextView.setText(nickname);



        // Send button
//        btnSend.setOnClickListener(v -> {
//            String message = messageInput.getText().toString().trim();
//            //sendMessage(message);
//        });

        // Scroll chat when user clicks the message input
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                chatScrollView.postDelayed(() -> chatScrollView.fullScroll(View.FOCUS_DOWN), 200);
            }
        });

        // add the events
        addEventDirectMessageReceived();
        addEventMessageReceivedOnOtherDevice();

        // add all messages to the initial view
        displayMessages();

        return view;
    }




    private void addEventMessageReceivedOnOtherDevice() {
        // add our hook to the event actions
        EventAction action = new EventAction(TAG + "-MessageReceivedOnOtherDevice"){
            @Override
            // expect a ChatMessage as first object"
            public void action(Object... data) {
                ChatMessage message = (ChatMessage) data[0];
                if(message == null){
                    return;
                }
                if (getActivity() == null) {
                    return;
                }
                // to update the UI in real-time this is needed
                getActivity().runOnUiThread(() -> {
                    updateReceivedMessage();
                });

            }
        };
        EventControl.addEvent(EventType.MESSAGE_DIRECT_UPDATE, action);
    }
    private void addEventDirectMessageReceived() {
        // add our hook to the event actions
        EventAction actionAddMessageReceived = new EventAction(TAG + "-DeviceChatFragmentMessageReceived"){
            @Override
            // expect a ChatMessage as first object and "receivedFromOutside as boolean"
            public void action(Object... data) {
                ChatMessage message = (ChatMessage) data[0];
                boolean receivedFromOutside = (boolean) data[1];
                Log.i("DeviceChatFragment", "Message received: " + message.getMessage());
                // to update the UI in real-time this is needed
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (receivedFromOutside) {
                            displayReceivedMessage(message);
                        } else {
                            displayUserMessage(message);
                        }
                    });
                }
            }
        };
        EventControl.addEvent(EventType.MESSAGE_DIRECT_RECEIVED, actionAddMessageReceived);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }


    private void displayMessages() {
        if(deviceId == null){
            return;
        }

        // get the previous messages


//        // get all the messages related to this device id
//        ChatMessages messageBox = ChatDatabaseWithDevice.getInstance(this.getContext()).getMessages(deviceId);
//        String thisDeviceId = Central.getInstance().getSettings().getIdDevice();
//        // add all the messages
//        for(ChatMessage message : messageBox.messages){
//            if(message.getAuthorId().equalsIgnoreCase(thisDeviceId)){
//                displayUserMessage(message);
//            }else{
//                displayReceivedMessage(message);
//            }
//        }
        // move to the bottom of the chat
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }


    /**
     * Adds a user message to the chat message container.
     *
     * @param message The message to display.
     */
    private void displayUserMessage(ChatMessage message) {
        View userMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_user_message, chatMessageContainer, false);
        TextView messageTextView = userMessageView.findViewById(R.id.message_user_self);
        messageTextView.setText(message.getMessage());

        // add the other details
        TextView textBoxUpper = userMessageView.findViewById(R.id.upper_text);
        TextView textBoxLower = userMessageView.findViewById(R.id.lower_text);

        long timeStamp = message.getTimestamp();
        String textLower = DateUtils.convertTimestampForChatMessage(timeStamp);

        if(message.delivered){
            textLower += " ✔";
        }

        if(message.read){
            textLower += "✔";
        }


        textBoxUpper.setText("");
        textBoxLower.setText(textLower);

        chatMessageContainer.addView(userMessageView);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }


    /**
     * Adds a received message to the chat message container.
     */
    private void updateReceivedMessage() {
        chatMessageContainer.removeAllViews();
        displayMessages();
    }


    /**
     * Adds a received message to the chat message container.
     *
     * @param message The message to display.
     */
    private void displayReceivedMessage(ChatMessage message) {
        View receivedMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_received_message, chatMessageContainer, false);

        // Get the objects
        TextView textBoxUpper = receivedMessageView.findViewById(R.id.message_boxUpper);
        TextView textBoxLower = receivedMessageView.findViewById(R.id.message_boxLower);


        // Add the timestamp
        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        textBoxUpper.setText("");
        textBoxLower.setText(dateText);
        // Set the sender's name
//        if (nickname.isEmpty() && message.getAuthorId() != null) {
//            textBoxLower.setText(message.getAuthorId());
//        } else {
//            String idText = nickname + "    " +
//                    dateText;
//            textBoxLower.setText(idText);
//        }



        // Set the message content
        String text = message.getMessage();
        // Removed (legacy) - tagBio, profile, and BioDatabase were part of old code
        // if(text.startsWith(tagBio) && profile != null){
        //     if(profile.getExtra() == null){
        //         profile.setExtra(ASCII.getRandomOneliner());
        //         BioDatabase.save(profile.getDeviceId(), profile, this.getContext());
        //     }
        //     text = profile.getExtra();
        // }
        TextView messageTextView = receivedMessageView.findViewById(R.id.message_user_1);
        messageTextView.setText(text);

        // Apply balloon style based on preferred background color
        // Removed (legacy) - profile was part of old code
        String colorBackground = "light gray";
        applyBalloonStyle(messageTextView, colorBackground);

        // Add click listener to navigate to the user profile
//        receivedMessageView.setOnClickListener(v -> {
//            if (profile != null) {
//                navigateToDeviceDetails(profile);
//            } else {
//                Toast.makeText(getContext(), "Profile not found", Toast.LENGTH_SHORT).show();
//            }
//        });

        // Add the view to the container
        chatMessageContainer.addView(receivedMessageView);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
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
