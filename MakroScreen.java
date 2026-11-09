package pl.hypemakro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MakroScreen extends Screen {
    private final Screen parent;

    public MakroScreen(Screen parent) {
        super(Text.translatable("hype_makro.gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 100;

        addDrawableChild(ButtonWidget.builder(enabledText(), b -> {
            MakroConfig.enabled = !MakroConfig.enabled;
            MakroConfig.save();
            b.setMessage(enabledText());
        }).dimensions(x, y, 220, 20).build());

        addDrawableChild(new CpsSlider(x, y + 26, 220, 20));

        addDrawableChild(ButtonWidget.builder(onlyBlocksText(), b -> {
            MakroConfig.onlyBlocks = !MakroConfig.onlyBlocks;
            MakroConfig.save();
            b.setMessage(onlyBlocksText());
        }).dimensions(x, y + 52, 220, 20).build());

        // NOWE: tryb woda + blok
        addDrawableChild(ButtonWidget.builder(waterText(), b -> {
            MakroConfig.waterPlace = !MakroConfig.waterPlace;
            MakroConfig.save();
            b.setMessage(waterText());
        }).dimensions(x, y + 84, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(waterDelayText(), b -> {
            MakroConfig.waterDelay = (MakroConfig.waterDelay + 1) % (MakroConfig.MAX_WATER_DELAY + 1);
            MakroConfig.save();
            b.setMessage(waterDelayText());
        }).dimensions(x, y + 110, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(switchOnlyText(), b -> {
            MakroConfig.switchOnly = !MakroConfig.switchOnly;
            MakroConfig.save();
            b.setMessage(switchOnlyText());
        }).dimensions(x, y + 136, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(collectText(), b -> {
            MakroConfig.autoCollect = !MakroConfig.autoCollect;
            MakroConfig.save();
            b.setMessage(collectText());
        }).dimensions(x, y + 162, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
            .dimensions(x, y + 194, 220, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int y = this.height / 2 - 100;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y - 26, 0xFFFFD54A);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hype_makro.gui.made_by"),
            this.width / 2, y - 14, 0xFFA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.translatable("hype_makro.gui.hint"),
            this.width / 2, y + 224, 0xFFA0A0A0);
    }

    @Override
    public void close() {
        MakroConfig.save();
        this.client.setScreen(this.parent);
    }

    private static Text onOff(boolean on) {
        return on
            ? Text.translatable("hype_makro.gui.on").formatted(Formatting.GREEN)
            : Text.translatable("hype_makro.gui.off").formatted(Formatting.RED);
    }

    private static Text enabledText() {
        return Text.translatable("hype_makro.gui.enabled", onOff(MakroConfig.enabled));
    }

    private static Text waterText() {
        return Text.translatable("hype_makro.gui.water", onOff(MakroConfig.waterPlace));
    }

    private static Text switchOnlyText() {
        return Text.translatable("hype_makro.gui.switch_only", onOff(MakroConfig.switchOnly));
    }

    private static Text collectText() {
        return Text.translatable("hype_makro.gui.collect", onOff(MakroConfig.autoCollect));
    }

    private static Text waterDelayText() {
        return Text.translatable("hype_makro.gui.water_delay", MakroConfig.waterDelay);
    }

    private static Text onlyBlocksText() {
        return Text.translatable("hype_makro.gui.only_blocks",
            MakroConfig.onlyBlocks
                ? Text.translatable("hype_makro.gui.yes").formatted(Formatting.GREEN)
                : Text.translatable("hype_makro.gui.no").formatted(Formatting.YELLOW));
    }

    static class CpsSlider extends SliderWidget {
        CpsSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), toValue(MakroConfig.cps));
            updateMessage();
        }

        static double toValue(int cps) {
            return (cps - 1) / 19.0;
        }

        int cps() {
            return MakroConfig.clampCps(1 + (int) Math.round(this.value * 19.0));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("hype_makro.gui.cps", cps()));
        }

        @Override
        protected void applyValue() {
            MakroConfig.cps = cps();
        }
    }
}
