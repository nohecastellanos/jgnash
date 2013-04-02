/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2013 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.ui.actions;

import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.ui.StaticUIMethods;
import jgnash.ui.UIApplication;
import jgnash.ui.components.OpenDatabaseDialog;
import jgnash.ui.util.SimpleSwingWorker;
import jgnash.util.FileUtils;
import jgnash.util.Resource;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI Action to open a database
 *
 * @author Craig Cavanaugh
 */
public class OpenAction {

    private static final Logger logger = Logger.getLogger(OpenAction.class.getName());

    private static boolean remoteConnectionFailed = false;

    static {
        logger.setLevel(Level.ALL);
    }

    public static void openAction() {

        final class BootEngine extends SimpleSwingWorker {

            private OpenDatabaseDialog dialog;

            protected BootEngine(final OpenDatabaseDialog dialog) {
                this.dialog = dialog;
            }

            @Override
            protected Void doInBackground() throws Exception {
                Resource rb = Resource.get();

                UIApplication.getFrame().displayWaitMessage(rb.getString("Message.PleaseWait"));

                EngineFactory.closeEngine(EngineFactory.DEFAULT);

                Engine e = null;

                final String user = dialog.getUserName();
                final char[] password = dialog.getPassword();

                if (dialog.isRemote()) {
                    String host = dialog.getHost();
                    int port = dialog.getPort();
                    boolean save = dialog.savePassword();

                    e = EngineFactory.bootClientEngine(host, port, user, password, EngineFactory.DEFAULT, save);

                    if (e == null) {
                        remoteConnectionFailed = true;
                    }
                } else {
                    if (FileUtils.isFileLocked(dialog.getDatabasePath())) {
                        StaticUIMethods.displayError(Resource.get().getString("Message.FileIsLocked"));
                    } else {

                        checkAndBackupOldVersion(dialog.getDatabasePath(), user, password);

                        e = EngineFactory.bootLocalEngine(dialog.getDatabasePath(), EngineFactory.DEFAULT, user, password);
                    }
                }

                if (e != null) {
                    e.getRootAccount(); // prime the engine
                }

                return null;
            }

            @Override
            protected void done() {
                logger.info("openAction() done");
                UIApplication.getFrame().stopWaitMessage();

                if (remoteConnectionFailed) {
                    StaticUIMethods.displayError(Resource.get().getString("Message.ErrorServerConnection"));
                }
            }
        }

        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {

                OpenDatabaseDialog d = new OpenDatabaseDialog(UIApplication.getFrame());

                d.setDatabasePath(EngineFactory.getLastDatabase());
                d.setUserName(EngineFactory.getLastUser());
                d.setPort(EngineFactory.getLastPort());
                d.setHost(EngineFactory.getLastHost());
                d.setPassword(EngineFactory.getLastPassword());
                d.setRemote(EngineFactory.getLastRemote());

                d.setVisible(true);

                boolean result = d.getResult();

                if (result) {
                    new BootEngine(d).execute();
                }

            }
        });
    }

    public static void openAction(final File file, final String user, final char[] password) {

        String database = file.getAbsolutePath();

        final class BootEngine extends SimpleSwingWorker {

            @Override
            protected Void doInBackground() throws Exception {
                Resource rb = Resource.get();
                UIApplication.getFrame().displayWaitMessage(rb.getString("Message.PleaseWait"));
                logger.fine("Booting the engine");

                // Disk IO is heavy so delay and allow the UI to react before starting the boot operation
                Thread.sleep(750);

                checkAndBackupOldVersion(file.getAbsolutePath(), user, password);

                Engine e = EngineFactory.bootLocalEngine(file.getAbsolutePath(), EngineFactory.DEFAULT, user, password);

                if (e != null) {
                    e.getRootAccount(); // prime the engine
                }

                logger.fine("Engine boot complete");
                return null;
            }

            @Override
            protected void done() {
                logger.info("openAction(final File file) done");
                UIApplication.getFrame().stopWaitMessage();
            }
        }

        if (EngineFactory.doesDatabaseExist(database)) {
            try {
                if (!FileUtils.isFileLocked(database)) {
                    new BootEngine().execute();
                } else {
                    StaticUIMethods.displayError(Resource.get().getString("Message.FileIsLocked"));
                }
            } catch (FileNotFoundException e) {
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }
    }

    public static void openLastAction() {
        final Logger appLogger = UIApplication.getLogger();

        final class BootEngine extends SimpleSwingWorker {

            @Override
            protected Void doInBackground() throws Exception {
                Resource rb = Resource.get();
                UIApplication.getFrame().displayWaitMessage(rb.getString("Message.PleaseWait"));
                logger.fine("Booting the engine");

                // Disk IO is heavy so delay and allow the UI to react before starting the boot operation
                Thread.sleep(750);

                Engine engine;

                final String user = EngineFactory.getLastUser();
                final char[] password = EngineFactory.getLastPassword();

                if (EngineFactory.getLastRemote() && EngineFactory.getLastPassword().length > 0) {

                    String host = EngineFactory.getLastHost();
                    int port = EngineFactory.getLastPort();

                    engine = EngineFactory.bootClientEngine(host, port, user, password, EngineFactory.DEFAULT, true);

                    if (engine == null) {
                        appLogger.warning(rb.getString("Message.ErrorServerConnection"));
                    }
                } else {    // must be a local file with a user name and password
                    checkAndBackupOldVersion(EngineFactory.getLastDatabase(), user, password);

                    engine = EngineFactory.bootLocalEngine(EngineFactory.getLastDatabase(), EngineFactory.DEFAULT, user, password);

                    if (engine == null) {
                        appLogger.warning(rb.getString("Message.ErrorLoadingFile"));
                    }
                }

                if (engine != null) {
                    engine.getRootAccount(); // prime the engine
                    logger.fine("Engine boot complete");
                }

                return null;
            }

            @Override
            protected void done() {
                logger.info("openLastAction() done");
                UIApplication.getFrame().stopWaitMessage();
            }
        }

        // check for locked file before trying the boot
        if (!EngineFactory.getLastRemote()) {
            String database = EngineFactory.getLastDatabase();

            if (EngineFactory.doesDatabaseExist(database)) {
                try {
                    if (!FileUtils.isFileLocked(database)) {
                        new BootEngine().execute();
                    } else {
                        StaticUIMethods.displayError(Resource.get().getString("Message.FileIsLocked"));
                    }
                } catch (FileNotFoundException e) {
                    appLogger.log(Level.SEVERE, e.toString(), e);
                }
            }
        }
    }

    public static void openRemote(final String host, final int port, final String user, final char[] password) {

        final class BootEngine extends SimpleSwingWorker {

            @Override
            protected Void doInBackground() throws Exception {
                Resource rb = Resource.get();
                UIApplication.getFrame().displayWaitMessage(rb.getString("Message.PleaseWait"));
                logger.fine("Booting the engine");

                Thread.sleep(750);

                EngineFactory.bootClientEngine(host, port, user, password, EngineFactory.DEFAULT, false);

                EngineFactory.getEngine(EngineFactory.DEFAULT).getRootAccount(); // prime the engine
                logger.fine("Engine boot complete");
                return null;
            }

            @Override
            protected void done() {
                UIApplication.getFrame().stopWaitMessage();
            }
        }

        new BootEngine().execute();
    }

    private static void checkAndBackupOldVersion(final String fileName, final String user, final char[] password) {

        if (Files.exists(new File(fileName).toPath())) {
            float version = EngineFactory.getFileVersion(new File(fileName), user, password);

            // make a versioned backup first
            if (version < Engine.CURRENT_VERSION) {
                FileUtils.copyFile(new File(fileName), new File(fileName + "." + version));
            }
        }
    }

    private OpenAction() {
    }
}
