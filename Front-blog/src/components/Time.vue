<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import {Clock} from "@element-plus/icons-vue";

// 日期
const time = ref()
const date = ref()
const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
const timerID = setInterval(updateTime, 1000);
updateTime();

function updateTime() {
  const cd = new Date();
  time.value = zeroPadding(cd.getHours(), 2) + ':' + zeroPadding(cd.getMinutes(), 2) + ':' + zeroPadding(cd.getSeconds(), 2);
  date.value = zeroPadding(cd.getFullYear(), 4) + '-' + zeroPadding(cd.getMonth() + 1, 2) + '-' + zeroPadding(cd.getDate(), 2) + ' ' + week[cd.getDay()];
}

function zeroPadding(num: number, digit: number) {
  let zero = '';
  for (let i = 0; i < digit; i++) {
    zero += '0';
  }
  return (zero + num).slice(-digit);
}

// 销毁
onUnmounted(() => {
  clearInterval(timerID);
})
</script>

<template>
  <div id="clock">
    <div class="icon">
      <el-icon><Clock/></el-icon>
       <span>电子时钟</span>
    </div>
    <span class="date">{{ date }}</span>
    <span class="time">{{ time }}</span>
  </div>
</template>

<style scoped lang="scss">
#clock {
  display: flex;
  flex-direction: column;

  text-align: center;
  // 夜间模式
  //color: #daf6ff;
  //text-shadow: 0 0 20px rgba(10, 175, 230, 1),  0 0 20px rgba(10, 175, 230, 0);
  text-shadow: 0 0 20px rgba(0, 0, 0, 0.2), 0 0 20px rgba(0, 0, 0, 0);

  .icon {
    font-size: 25px;
    font-weight: bold;
    padding: 5px 0;
    margin: 0 auto;
  }

  .time {
    color: #aca0a0;
    font-family: 'Share Tech Mono', monospace;
    letter-spacing: 0.05em;
    font-size: 40px;
  }

  .date {
    font-family: 'Share Tech Mono', monospace;
    color: #aca0a0;
    letter-spacing: 0.1em;
    font-size: 15px;

  }
}
</style>