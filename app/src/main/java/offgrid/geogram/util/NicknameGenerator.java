package offgrid.geogram.util;

import java.util.Random;

public class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "Bright", "Calm", "Quick", "Wise", "Swift", "True", "Bold", "Kind",
            "Brave", "Quiet", "Sunny", "Sharp", "Loyal", "Faithful", "Happy",
            "Strong", "Cheerful", "Humble", "Neat", "Trusty", "Ready", "Smooth",
            "Steady", "Clear", "Clever", "Able", "Fresh", "Fast", "Smart",
            "Nimble", "Cool", "Sure", "Sharp", "Neat", "Light", "Warm"
    };

    private static final String[] NOUNS = {
            "Scout", "Guide", "Path", "Spark", "Trail", "Beam", "Star",
            "Light", "Way", "Hope", "Haven", "Base", "Link", "Sky",
            "Guard", "Fix", "Hero", "Pack", "Gear", "Shed", "Torch",
            "Camp", "Nest", "Crew", "Lift", "Hand", "Net", "Plan",
            "Code", "Line", "Wing", "Key", "Map", "Aid", "Root",
            "Home", "Unit"
    };

    private static final String[] INTRO_PARTS_START = {
            "Sustainability advocate",
            "Exploring privacy",
            "Building a solar-powered future",
            "Lover of permaculture",
            "Crypto enthusiast",
            "Passionate about independence",
            "Decentralization advocate",
            "Harnessing solar energy",
            "Committed to off-grid living",
            "Privacy and freedom seeker"
    };

    private static final String[] INTRO_PARTS_MIDDLE = {
            "with a focus on resilience",
            "embracing decentralized systems",
            "powered by renewable energy",
            "living close to nature",
            "exploring blockchain innovation",
            "dedicated to sustainable living",
            "with a passion for open-source tech",
            "believing in a brighter future",
            "supporting community-driven solutions"
    };

    private static final String[] INTRO_PARTS_END = {
            "off the grid.",
            "for a sustainable tomorrow.",
            "with a privacy-first mindset.",
            "through clean technology.",
            "for decentralized communities.",
            "in harmony with nature.",
            "with freedom and self-reliance.",
            "driven by innovation and hope.",
            "building better systems."
    };

    private static final Random RANDOM = new Random();

    /**
     * Generates a random readable nickname.
     *
     * @return A random nickname in the format "AdjectiveNoun123"
     */
    public static String generateNickname() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        int number = RANDOM.nextInt(1000); // Generate a random number between 0 and 999
        return adjective + noun + number;
    }

    /**
     * Generates a random intro by mixing parts.
     *
     * @return A random intro under 100 characters.
     */
    public static String generateIntro() {
        String start = INTRO_PARTS_START[RANDOM.nextInt(INTRO_PARTS_START.length)];
        String middle = INTRO_PARTS_MIDDLE[RANDOM.nextInt(INTRO_PARTS_MIDDLE.length)];
        String end = INTRO_PARTS_END[RANDOM.nextInt(INTRO_PARTS_END.length)];

        String combined = start + " " + middle + " " + end;

        // Ensure the length doesn't exceed 100 characters
        if (combined.length() > 100) {
            combined = combined.substring(0, 97) + "...";
        }

        return combined;
    }

    public static void main(String[] args) {
        // Generate and print 10 random nicknames as examples
        System.out.println("Nicknames:");
        for (int i = 0; i < 10; i++) {
            System.out.println(generateNickname());
        }

        // Generate and print 10 random intros as examples
        System.out.println("\nIntros:");
        for (int i = 0; i < 10; i++) {
            System.out.println(generateIntro());
        }
    }
}
