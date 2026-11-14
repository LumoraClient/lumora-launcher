package dev.lumora.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.core.render.ScissorStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The scissor rectangle follows HudGraphics.translate/scale: the 1.17.1 pose matrix must reach ScissorStack in its layout. */
class GuiGraphicsScissorTest {
    @Test
    void scissorRectangleFollowsThePose() {
        PoseStack pose = new PoseStack();
        pose.translate(300, 150, 0);
        pose.scale(2, 0.5f, 1);
        ScissorStack.Rect r = ScissorStack.transform(5, 10, 45, 30, GuiGraphics.values(pose.last().pose()));
        assertEquals(new ScissorStack.Rect(310, 155, 390, 165), r);
    }

    @Test
    void nestedPanelScissorIsClippedToTheOuterOne() {
        ScissorStack stack = new ScissorStack();
        PoseStack pose = new PoseStack();
        stack.push(ScissorStack.transform(300, 150, 400, 200, GuiGraphics.values(pose.last().pose())));
        pose.translate(300, 150, 0); // ChatInputBar draws the panel in its own coordinates
        ScissorStack.Rect inner = stack.push(ScissorStack.transform(-20, 10, 50, 80, GuiGraphics.values(pose.last().pose())));
        assertEquals(new ScissorStack.Rect(300, 160, 350, 200), inner);
        assertEquals(new ScissorStack.Rect(300, 150, 400, 200), stack.pop());
    }
}
