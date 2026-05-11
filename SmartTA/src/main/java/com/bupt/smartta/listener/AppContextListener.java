package com.bupt.smartta.listener;

import com.bupt.smartta.util.DataStore;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Servlet context lifecycle listener that initialises the Smart-TA application on startup.
 *
 * <p>When Tomcat starts the SmartTA web application, this listener automatically triggers
 * the initialisation of the {@link com.bupt.smartta.util.DataStore} singleton, which
 * loads all JSON data files and creates seed data if the data directory is empty.</p>
 *
 * <p>This ensures the Single Source of Truth principle: all data is managed by the
 * backend JSON files, and no state is held in memory across application restarts.</p>
 *
 * @see DataStore
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[SmartTA] AppContextListener: Initializing DataStore...");
        try {
            // Trigger DataStore singleton initialisation (thread-safe via double-checked locking)
            // getInstance() internally calls loadAll(), which loads all JSON and seeds if empty
            DataStore ds = DataStore.getInstance();
            System.out.println("[SmartTA] AppContextListener: DataStore initialized successfully.");
            System.out.println("[SmartTA] AppContextListener: Positions=" + ds.getPositions().size()
                    + ", Applicants=" + ds.getApplicants().size()
                    + ", Applications=" + ds.getApplications().size()
                    + ", Users=" + ds.getUsers().size()
                    + ", Config loaded=" + (ds.getSystemConfig() != null));
        } catch (Exception e) {
            System.err.println("[SmartTA] AppContextListener: FAILED to initialize DataStore: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[SmartTA] AppContextListener: Application shutting down.");
    }
}
