package com.itheima.controller;

import com.itheima.service.DashboardService;
import com.itheima.vo.DashboardStatsVO;
import com.itheima.pojo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/stats")
    public Result<DashboardStatsVO> getDashboardStats() {
        DashboardStatsVO stats = dashboardService.getDashboardStats();
        return Result.success(stats);
    }
}