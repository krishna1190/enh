package org.jessies.p9term;

import e.forms.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.List;
import javax.swing.*;

public class P9TermPreferences extends Preferences {
    public static final String BACKGROUND_COLOR = "background";
    public static final String CURSOR_COLOR = "cursorColor";
    public static final String FOREGROUND_COLOR = "foreground";
    public static final String SELECTION_COLOR = "selectionColor";
    
    public static final String ALPHA = "alpha";
    public static final String ANTI_ALIAS = "antiAlias";
    public static final String BLINK_CURSOR = "cursorBlink";
    public static final String BLOCK_CURSOR = "blockCursor";
    public static final String FANCY_BELL = "fancyBell";
    public static final String VISUAL_BELL = "visualBell";
    public static final String FONT = "font";
    public static final String HIDE_MOUSE_WHEN_TYPING = "hideMouseWhenTyping";
    public static final String INITIAL_COLUMN_COUNT = "initialColumnCount";
    public static final String INITIAL_ROW_COUNT = "initialRowCount";
    public static final String SCROLL_ON_KEY_PRESS = "scrollKey";
    public static final String SCROLL_ON_TTY_OUTPUT = "scrollTtyOutput";
    
    /**
     * Whether or not the alt key should be meta.
     * If true, you can't use alt as part of your system's input method.
     * If false, you can't comfortably use emacs(1).
     */
    public static final String USE_ALT_AS_META = "useAltAsMeta";
    
    private static final Color LIGHT_BLUE = new Color(0xb3d4ff);
    private static final Color NEAR_BLACK = new Color(0x181818);
    private static final Color NEAR_GREEN = new Color(0x72ff00);
    private static final Color NEAR_WHITE = new Color(0xeeeeee);
    private static final Color SELECTION_BLUE = new Color(0x1c2bff);
    private static final Color VERY_DARK_BLUE = new Color(0x000045);
    
    protected String getPreferencesFilename() {
        return System.getProperty("org.jessies.p9term.optionsFile");
    }
    
    protected void initPreferences() {
        setHelperForClass(Double.class, new AlphaHelper());
        
        addPreference(ALPHA, Double.valueOf(1.0), "Terminal opacity");
        addPreference(ANTI_ALIAS, Boolean.TRUE, "Anti-alias text?");
        addPreference(BLINK_CURSOR, Boolean.TRUE, "Blink cursor?");
        addPreference(BLOCK_CURSOR, Boolean.FALSE, "Use block cursor?");
        addPreference(FANCY_BELL, Boolean.TRUE, "High-quality rendering of the visual bell?");
        addPreference(VISUAL_BELL, Boolean.TRUE, "Visual bell (as opposed to no bell)?");
        addPreference(FONT, new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 12), "Font");
        addPreference(HIDE_MOUSE_WHEN_TYPING, Boolean.TRUE, "Hide mouse when typing");
        addPreference(INITIAL_COLUMN_COUNT, Integer.valueOf(80), "New terminal width");
        addPreference(INITIAL_ROW_COUNT, Integer.valueOf(24), "New terminal height");
        addPreference(SCROLL_ON_KEY_PRESS, Boolean.TRUE, "Scroll to bottom on key press?");
        addPreference(SCROLL_ON_TTY_OUTPUT, Boolean.FALSE, "Scroll to bottom on output?");
        addPreference(USE_ALT_AS_META, Boolean.FALSE, "Use alt key as meta key (for Emacs)?");
        
        // Defaults reminiscent of SGI's xwsh(1).
        addPreference(BACKGROUND_COLOR, Color.WHITE, "Background");
        addPreference(CURSOR_COLOR, Color.BLUE, "Cursor");
        addPreference(FOREGROUND_COLOR, Color.BLACK, "Text foreground");
        addPreference(SELECTION_COLOR, LIGHT_BLUE, "Selection background");
    }
    
    @Override
    protected void willAddRows(List<FormPanel> formPanels) {
        // Offer various preset color combinations.
        // Note that these get fossilized into the user's preferences; updating values here doesn't affect users who've already clicked the button.
        formPanels.get(1).addRow("Presets:", makePresetButton("  Terminator  ", VERY_DARK_BLUE, NEAR_WHITE, Color.GREEN, SELECTION_BLUE));
        formPanels.get(1).addRow("", makePresetButton("Black on White", Color.WHITE, NEAR_BLACK, Color.BLUE, LIGHT_BLUE));
        formPanels.get(1).addRow("", makePresetButton("Green on Black", Color.BLACK, NEAR_GREEN, Color.GREEN, SELECTION_BLUE));
        formPanels.get(1).addRow("", makePresetButton("White on Black", Color.BLACK, NEAR_WHITE, Color.GREEN, Color.DARK_GRAY));
    }
    
    private JComponent makePresetButton(String name, final Color background, final Color foreground, final Color cursor, final Color selection) {
        // FIXME: ideally, we'd update the button image when the user changes the anti-aliasing preference.
        JButton button = new JButton(new ImageIcon(makePresetButtonImage(name, background, foreground)));
        button.putClientProperty("JButton.buttonType", "gradient"); // Mac OS 10.5
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                put(BACKGROUND_COLOR, background);
                put(FOREGROUND_COLOR, foreground);
                put(CURSOR_COLOR, cursor);
                put(SELECTION_COLOR, selection);
            }
        });
        return button;
    }
    
    private BufferedImage makePresetButtonImage(String name, Color background, Color foreground) {
        // Make a representative image for the button.
        BufferedImage image = makeEmptyPresetButtonImageToFit(name);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, getBoolean(P9TermPreferences.ANTI_ALIAS) ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(getFont(P9TermPreferences.FONT));
        g.setColor(background);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(background.darker());
        g.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);
        g.setColor(foreground);
        Rectangle2D stringBounds = g.getFontMetrics().getStringBounds(name, g);
        final int x = (image.getWidth() - (int) stringBounds.getWidth()) / 2;
        int y = (image.getHeight() - (int) stringBounds.getHeight()) / 2 + g.getFontMetrics().getAscent();
        y = (int) (y / 1.1);
        g.drawString(name, x, y);
        g.dispose();
        return image;
    }
    
    private BufferedImage makeEmptyPresetButtonImageToFit(String name) {
        // This seems ugly and awkward, but I can't think of a simpler way to get a suitably-sized BufferedImage to work with, without hard-coding dimensions.
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setFont(getFont(P9TermPreferences.FONT));
        FontMetrics metrics = g.getFontMetrics();
        final int height = (int) (1.4* metrics.getHeight());
        final int width = (int) (metrics.getStringBounds(name, g).getWidth() * 1.2);
        g.dispose();
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }
    
    public double getDouble(String key) {
        return (Double) get(key);
    }
    
    private class AlphaHelper implements PreferencesHelper {
        public String encode(String key) {
            return Double.toString(getDouble(key));
        }
        
        public Object decode(String valueString) {
            return Double.valueOf(valueString);
        }
        
        public void addRow(List<FormPanel> formPanels, final String key, final String description) {
            final JSlider slider = new JSlider(0, 255);
            slider.setValue((int) (255 * getDouble(key)));
            slider.addChangeListener(new javax.swing.event.ChangeListener() {
                public void stateChanged(javax.swing.event.ChangeEvent e) {
                    put(key, ((double) slider.getValue())/256);
                }
            });
            slider.setEnabled(GuiUtilities.isWindows() == false);
            formPanels.get(1).addRow(description + ":", slider);
        }
    }
}
