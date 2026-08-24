package tanks.gui.screen;

import tanks.Drawing;
import tanks.Game;
import tanks.gui.Button;

public class ScreenStoragePermission extends Screen
{
    Button selectFolder = new Button(this.centerX, this.centerY + 50 + this.textSize, this.objWidth, this.objHeight, "Select folder", () -> {
        if (Game.framework == Game.Framework.lwjgl)
        {
            System.out.println("Select folder not available on lwjgl.");
            Game.screen = new ScreenTitle();
        }
        else
        {
            System.out.println(System.getProperty("java.vendor"));
        }
    });

    @Override
    public void update()
    {
        this.selectFolder.update();
    }

    @Override
    public void draw()
    {
        this.drawDefaultBackground();
        Drawing.drawing.setColor(0, 0, 0, 127);
        Drawing.drawing.drawPopup(centerX, centerY, 1100, 720);
        Drawing.drawing.setColor(255, 255, 255);
        Drawing.drawing.setInterfaceFontSize(this.textSize);
        Drawing.drawing.drawText(this.centerX, this.centerY - 50, "Please select a folder for tanks to use for Game Data.");
        Drawing.drawing.drawText(this.centerX, this.centerY - 50 + this.textSize, "If you have played an older version of Tanks,");
        Drawing.drawing.drawText(this.centerX, this.centerY - 50 + this.textSize * 2, "your data will be moved to this folder.");
        this.selectFolder.draw();
    }
}
