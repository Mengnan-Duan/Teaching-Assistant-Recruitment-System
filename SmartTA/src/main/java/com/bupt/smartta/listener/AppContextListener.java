package com.bupt.smartta.listener;

import com.bupt.smartta.util.DataStore;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * 应用启动监听器。
 * 当 Tomcat 启动 SmartTA 应用时，自动初始化 DataStore，加载/创建所有数据。
 * 确保 Single Source of Truth（单一数据源）：后端 JSON 文件。
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[SmartTA] AppContextListener: Initializing DataStore...");
        try {
            // 触发 DataStore 单例初始化（线程安全）
            // getInstance() 内部调用 loadAll()，会加载所有 JSON 并在空时自动 seed
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
