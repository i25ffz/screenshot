/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.screenshot;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;


/**
 * Gather a screen shot from the device and save it to a file.
 */
public class ScreenShotDialog extends Dialog {

    private Label mBusyLabel;
    private Label mImageLabel;
    private Button mSave;
    private IDevice mDevice;
    private RawImage mRawImage;
    private Clipboard mClipboard;
    private Point mDownPoint = new Point(0, 0);
    private double mScale = 1.0d;

	/** Number of 90 degree rotations applied to the current image */
    private int mRotateCount = 0;

    /**
     * Create with default style.
     */
    public ScreenShotDialog(Shell parent) {
        this(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        mClipboard = new Clipboard(parent.getDisplay());
    }

    /**
     * Create with app-defined style.
     */
    public ScreenShotDialog(Shell parent, int style) {
        super(parent, style);
    }

	public void setScale(double mScale) {
		this.mScale = mScale;
	}
    
    /**
     * Prepare and display the dialog.
     * @param device The {@link IDevice} from which to get the screenshot.
     */
    public void open(IDevice device) {
        mDevice = device;

        Shell parent = getParent();
        Shell shell = new Shell(parent, getStyle());
        shell.setText("Device Screen Capture");

        createContents(shell);
        shell.pack();
        shell.open();

        updateDeviceImage(shell);

        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }

    }

    private void refreshImage(final Shell shell) {
        updateDeviceImage(shell);
        // RawImage only allows us to rotate the image 90 degrees at the time,
        // so to preserve the current rotation we must call getRotated()
        // the same number of times the user has done it manually.
        // TODO: improve the RawImage class.
        for (int i=0; i < mRotateCount; i++) {
            mRawImage = mRawImage.getRotated();
        }
        updateImageDisplay(shell);
    }
    
    private void runShellCmd(final Shell shell, String strCmd) {
		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(strCmd);
			proc.waitFor();
			
			int exitVal = proc.exitValue();
			System.out.println("Process[" + strCmd + "] exitValue: " + exitVal);
			Thread.sleep(1000);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InterruptedException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		refreshImage(shell);
    }
    
    /**
     * Create place-holder art with the specified color.
     */
    private Image createPlaceHolderArt(Display display, int width,
            int height, Color color) {
        Image img = new Image(display, width, height);
        GC gc = new GC(img);
        gc.setForeground(color);
        gc.drawLine(0, 0, width, height);
        gc.drawLine(0, height - 1, width, -1);
        gc.dispose();
        return img;
    }
    
    /*
     * Create the screen capture dialog contents.
     */
    private void createContents(final Shell shell) {
        GridData data;

        final int colCount = 7;

        shell.setLayout(new GridLayout(colCount, true));

        // "refresh" button
        Button refresh = new Button(shell, SWT.PUSH);
        refresh.setText("Refresh");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        refresh.setLayoutData(data);
        refresh.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	refreshImage(shell);
            }
        });

        // "rotate" button
        Button rotate = new Button(shell, SWT.PUSH);
        rotate.setText("Rotate");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        rotate.setLayoutData(data);
        rotate.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mRawImage != null) {
                    mRotateCount = (mRotateCount + 1) % 4;
                    mRawImage = mRawImage.getRotated();
                    updateImageDisplay(shell);
                }
            }
        });

        // "save" button
        mSave = new Button(shell, SWT.PUSH);
        mSave.setText("Save");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        mSave.setLayoutData(data);
        mSave.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                saveImage(shell);
            }
        });

        Button copy = new Button(shell, SWT.PUSH);
        copy.setText("Copy");
        copy.setToolTipText("Copy the screenshot to the clipboard");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        copy.setLayoutData(data);
        copy.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copy();
            }
        });


        // "done" button
        Button done = new Button(shell, SWT.PUSH);
        done.setText("Done");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        done.setLayoutData(data);
        done.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.close();
            }
        });
        
        // "home" button
        Button home = new Button(shell, SWT.PUSH);
        home.setText("Home");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        home.setLayoutData(data);
        home.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	runShellCmd(shell, "adb shell input keyevent 3");
            }
        });

        // "back" button
        Button back = new Button(shell, SWT.PUSH);
        back.setText("Back");
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.widthHint = 80;
        back.setLayoutData(data);
        back.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	runShellCmd(shell, "adb shell input keyevent 4");
            }
        });
        
        // title/"capturing" label
        mBusyLabel = new Label(shell, SWT.NONE);
        mBusyLabel.setText("Preparing...");
        data = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        data.horizontalSpan = colCount;
        mBusyLabel.setLayoutData(data);

        // space for the image
        mImageLabel = new Label(shell, SWT.BORDER);
        data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
        data.horizontalSpan = colCount;
        mImageLabel.setLayoutData(data);
        Display display = shell.getDisplay();
        mImageLabel.setImage(createPlaceHolderArt(
                display, 50, 50, display.getSystemColor(SWT.COLOR_BLUE)));

        mImageLabel.addMouseListener(new MouseListener() {

            @Override
            public void mouseUp(MouseEvent event) {
                System.out.println("mouseUp:" + event);
                String strCmd;
				if (event.x == mDownPoint.x && event.y == mDownPoint.y) {
					strCmd = String.format("adb shell input tap %d %d", (int)(event.x / mScale), (int)(event.y / mScale));
				} else {
					strCmd = String.format("adb shell input swipe %d %d %d %d",
							(int) (mDownPoint.x / mScale),
							(int) (mDownPoint.y / mScale),
							(int) (event.x / mScale), (int) (event.y / mScale));
				}
				
				runShellCmd(shell, strCmd);
            }

            @Override
            public void mouseDown(MouseEvent event) {
            	System.out.println("mouseDown:" + event);
            	mDownPoint.x = event.x;
            	mDownPoint.y = event.y;
            }

            @Override
            public void mouseDoubleClick(MouseEvent arg0) {
            	System.out.println("mouseDoubleClick:" + arg0);
            }
        });
        
        shell.setDefaultButton(done);
    }

    /**
     * Copies the content of {@link #mImageLabel} to the clipboard.
     */
    private void copy() {
        mClipboard.setContents(
                new Object[] {
                        mImageLabel.getImage().getImageData()
                }, new Transfer[] {
                        ImageTransfer.getInstance()
                });
    }

    /**
     * Captures a new image from the device, and display it.
     */
    private void updateDeviceImage(Shell shell) {
        mBusyLabel.setText("Capturing...");     // no effect

        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

        mRawImage = getDeviceImage();

        updateImageDisplay(shell);
    }

    /**
     * Updates the display with {@link #mRawImage}.
     * @param shell
     */
    private void updateImageDisplay(Shell shell) {
        Image image;
        if (mRawImage == null) {
            Display display = shell.getDisplay();
            image = createPlaceHolderArt(
                    display, 320, 240, display.getSystemColor(SWT.COLOR_BLUE));

            mSave.setEnabled(false);
            mBusyLabel.setText("Screen not available");
        } else {
            // convert raw data to an Image.
            PaletteData palette = new PaletteData(
                    mRawImage.getRedMask(),
                    mRawImage.getGreenMask(),
                    mRawImage.getBlueMask());

            ImageData imageData = new ImageData(mRawImage.width, mRawImage.height,
                    mRawImage.bpp, palette, 1, mRawImage.data);
            imageData = imageData.scaledTo((int)(mRawImage.width * mScale), (int)(mRawImage.height * mScale));
            image = new Image(getParent().getDisplay(), imageData);

            mSave.setEnabled(true);
            mBusyLabel.setText("Captured image:");
        }

        mImageLabel.setImage(image);
        mImageLabel.pack();
        shell.pack();

        // there's no way to restore old cursor; assume it's ARROW
        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
    }

    /**
     * Grabs an image from an ADB-connected device and returns it as a {@link RawImage}.
     */
    private RawImage getDeviceImage() {
        try {
            return mDevice.getScreenshot();
        }
        catch (IOException ioe) {
            Log.w("ddms", "Unable to get frame buffer: " + ioe.getMessage());
            return null;
        } catch (TimeoutException e) {
            Log.w("ddms", "Unable to get frame buffer: timeout ");
            return null;
        } catch (AdbCommandRejectedException e) {
            Log.w("ddms", "Unable to get frame buffer: " + e.getMessage());
            return null;
        }
    }

    /*
     * Prompt the user to save the image to disk.
     */
    private void saveImage(Shell shell) {
        FileDialog dlg = new FileDialog(shell, SWT.SAVE);

        Calendar now = Calendar.getInstance();
        String fileName = String.format("device-%tF-%tH%tM%tS.png",
                now, now, now, now);

        dlg.setText("Save image...");
        dlg.setFileName(fileName);

        String lastDir = "D:\\";
//        String lastDir = DdmUiPreferences.getStore().getString("lastImageSaveDir");
//        if (lastDir.length() == 0) {
//            lastDir = DdmUiPreferences.getStore().getString("imageSaveDir");
//        }
        dlg.setFilterPath(lastDir);
        dlg.setFilterNames(new String[] {
            "PNG Files (*.png)"
        });
        dlg.setFilterExtensions(new String[] {
            "*.png" //$NON-NLS-1$
        });

        fileName = dlg.open();
        if (fileName != null) {
            // FileDialog.getFilterPath() does NOT always return the current
            // directory of the FileDialog; on the Mac it sometimes just returns
            // the value the dialog was initialized with. It does however return
            // the full path as its return value, so just pick the path from
            // there.
            if (!fileName.endsWith(".png")) {
                fileName = fileName + ".png";
            }

//            String saveDir = new File(fileName).getParent();
//            if (saveDir != null) {
//                DdmUiPreferences.getStore().setValue("lastImageSaveDir", saveDir);
//            }

            Log.d("ddms", "Saving image to " + fileName);
            ImageData imageData = mImageLabel.getImage().getImageData();

            try {
                org.eclipse.swt.graphics.ImageLoader loader =
                        new org.eclipse.swt.graphics.ImageLoader();

                loader.data = new ImageData[] { imageData };
                loader.save(fileName, SWT.IMAGE_PNG);
            }
            catch (SWTException e) {
                Log.w("ddms", "Unable to save " + fileName + ": " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
    	Display display = new Display();
    	final Shell shell = new Shell(display);

    	double scale = 1.0d;
    	//parse command line parameters.
        int index = 0;
        do {
            String argument = args[index++];

            if ("-s".equals(argument)) {
                // quick check on the next argument.
                if (index == args.length) {
                    printAndExit("Missing scale number after -s", false /* terminate */);
                }

                scale = Double.parseDouble(args[index++]);
            }
        } while (index < args.length);
    	
    	System.out.println("scale:" + scale);
        
        // init the lib
        // [try to] ensure ADB is running
        String adbLocation = System.getProperty("com.android.screenshot.bindir"); //$NON-NLS-1$
        if (adbLocation != null && adbLocation.length() != 0) {
            adbLocation += File.separator + "adb"; //$NON-NLS-1$
        } else {
            adbLocation = "adb"; //$NON-NLS-1$
        }

        AndroidDebugBridge.init(false /* debugger support */);

        try {
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                    adbLocation, false /* forceNewBridge */);

            // we can't just ask for the device list right away, as the internal thread getting
            // them from ADB may not be done getting the first list.
            // Since we don't really want getDevices() to be blocking, we wait here manually.
            int count = 0;
            while (bridge.hasInitialDeviceList() == false) {
                try {
                    Thread.sleep(100);
                    count++;
                } catch (InterruptedException e) {
                    // pass
                }

                // let's not wait > 10 sec.
                if (count > 100) {
                    System.err.println("Timeout getting device list!");
                    return;
                }
            }

            // now get the devices
            IDevice[] devices = bridge.getDevices();

            if (devices.length == 0) {
                printAndExit("No devices found!", true /* terminate */);
            }

            IDevice target = null;

            if (devices.length > 1) {
                printAndExit("Error: more than one emulator or device available!",
                        true /* terminate */);
            }
            target = devices[0];

            if (target != null) {
                try {
                    System.out.println("Taking screenshot from: " + target.getSerialNumber());
                    
                	ScreenShotDialog dlg = new ScreenShotDialog(shell);
                	dlg.setScale(scale);
                	dlg.open(target);
                	
                    System.out.println("Success.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                printAndExit("Could not find matching device/emulator.", true /* terminate */);
            }
        } finally {
            AndroidDebugBridge.terminate();
        }
    }
    
    private static void printAndExit(String message, boolean terminate) {
        System.out.println(message);
        if (terminate) {
            AndroidDebugBridge.terminate();
        }
        System.exit(1);
    }
    
}

