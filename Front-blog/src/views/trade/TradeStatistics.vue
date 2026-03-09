<template>
  <div class="dashboard-container">
    <!-- 顶部统计卡片 -->
    <div class="stats-cards">
      <el-card class="stats-card">
        <div class="card-content">
          <div class="label">用户数量</div>
          <div class="value">{{ stats.userCount || 0 }}</div>
        </div>
      </el-card>
      <el-card class="stats-card">
        <div class="card-content">
          <div class="label">闲置商品数量</div>
          <div class="value">{{ stats.goodsCount || 0 }}</div>
        </div>
      </el-card>
    </div>

    <!-- 饼图 -->
    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>不同商品分类闲置商品数量统计</span>
        </div>
      </template>
      <div ref="pieChartRef" class="pie-chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'

const pieChartRef = ref(null)
let pieChart = null
const stats = ref({
  userCount: 0,
  goodsCount: 0,
  categoryStats: []
})

// 获取后端数据
const fetchStats = async () => {
  try {
    const res = await axios.get('/dashboard/stats')
    if (res.data.code === 1) {
      stats.value = res.data.data
      renderPieChart()
    }
  } catch (err) {
    console.error('获取统计数据失败', err)
  }
}

// 渲染饼图
const renderPieChart = () => {
  if (!pieChartRef.value) return
  if (pieChart) {
    pieChart.dispose()
  }
  pieChart = echarts.init(pieChartRef.value)

  const { categoryStats } = stats.value
  const pieData = categoryStats.map(item => ({
    name: item.categoryName,
    value: item.count,
    itemStyle: { color: item.color }
  }))

  const option = {
    title: {
      text: '不同商品分类闲置商品数量统计',
      left: 'center',
      textStyle: { fontSize: 14 }
    },
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 'middle',
      data: categoryStats.map(item => item.categoryName)
    },
    series: [
      {
        name: '分类统计',
        type: 'pie',
        radius: '60%',
        center: ['60%', '55%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{c}件'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: pieData
      }
    ]
  }

  pieChart.setOption(option)
  window.addEventListener('resize', () => pieChart.resize())
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stats-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.card-content {
  padding: 16px;
}

.label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.chart-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.pie-chart {
  width: 100%;
  height: 500px;
}
</style>