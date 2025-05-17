package offgrid.geogram.util;

import java.util.Random;

public class ASCII {

    public static final String[] oneliners = new String[]{
            "|-o-|",
            //"C|_|",
            "(^_^)",
            ">>------>",
            "{-_-}",
            "(<>..<>)",
            "//\\(oo)/\\\\",
            "<*_*>",
            "><(((('>",
            "><>",
            "--------{---(@",
            "(♥_♥)",
            "ε(´סּ︵סּ`)з",
            "d[ o_0 ]b",
            "⺌∅‿∅⺌",
            "¯\\_(ツ)_/¯",
            "d[-_-]b",
            "< )))) ><",
            "(ಠ_ಠ)",
            "t(-.-t)",
            "(\\/) (;,,;) (\\/)",
            "¯＼(º_o)/¯",
            "(⌐■_■)"
    };

    /**
     * Returns a random oneliner from the predefined list.
     *
     * @return A randomly selected oneliner.
     */
    public static String getRandomOneliner() {
        Random random = new Random();
        int index = random.nextInt(oneliners.length);
        return oneliners[index];
    }
}
