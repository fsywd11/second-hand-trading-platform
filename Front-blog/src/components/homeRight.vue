<script lang="js" setup>
import fsy from "@/assets/fsy.png";
import articleInfoStore from '@/stores/ArticleInfo.js';
import categoryManageStore from '@/stores/categoryInfo.js'
import { ref, onMounted, onUnmounted } from "vue";
import Time from "@/components/Time.vue";
import { ElTooltip } from 'element-plus'
import commentInfoStore from '@/stores/CommentInfo.js'
import NewArticle from "@/components/NewArticle.vue";
// 假设这些数据从相应的数据源获取
const articles = articleInfoStore();
const categoryInfoStore = categoryManageStore();
const commentInfo = commentInfoStore();
// 获取controllerInfoStore.info数组的长度
const infoLength = ref(articles.info? articles.info.length : 0);
const categoryLength = ref(categoryInfoStore.info? categoryInfoStore.info.length : 0);
const commentlength = ref(commentInfo.info? commentInfo.info.length : 0);
const articleCount = ref(infoLength);
const categoryCount = ref(categoryLength);
const commentCount = ref(commentlength);

//电子时钟
const time = ref('');

// 更新当前时间
const updateTime = () => {
  const now = new Date();
  time.value = now.toLocaleTimeString(); // 格式为hh:mm:ss
};

// 组件挂载后启动定时器
onMounted(() => {
  updateTime();
  const interval = setInterval(updateTime, 1000); // 每秒更新一次时间

  // 清理定时器，防止内存泄漏
  onUnmounted(() => {
    clearInterval(interval);
  });

  // 使用 IntersectionObserver 实现渐显效果
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        if (entry.target.classList.contains('square-frame')) {
          isSquareFrameVisible.value = true;
        } else if (entry.target.classList.contains('notice-board')) {
          isNoticeBoardVisible.value = true;
        } else if (entry.target.classList.contains('clock')) {
          isClockVisible.value = true;
        }
        observer.unobserve(entry.target);
      }
    });
  });

  const squareFrame = document.querySelector('.square-frame');
  const noticeBoard = document.querySelector('.notice-board');
  const clock = document.querySelector('.clock');

  if (squareFrame) observer.observe(squareFrame);
  if (noticeBoard) observer.observe(noticeBoard);
  if (clock) observer.observe(clock);

  onUnmounted(() => {
    observer.disconnect();
  });
});

const isSquareFrameVisible = ref(false);
const isNoticeBoardVisible = ref(false);
const isClockVisible = ref(false);

// 定义跳转到哔哩哔哩官网的函数
const goToBilibili = () => {
  window.open('https://www.bilibili.com', '_blank');
};

const goToGitHub = () => {
  window.open('https://github.com', '_blank');
};
</script>

<template>
  <div class="avatar-container no-select">
    <div class="square-frame" :class="{ 'fade-in-up': isSquareFrameVisible }">
      <!-- 第一部分：占 70%，背景颜色为深蓝色，放置头像和用户名 -->

      <div class="part-one">
        <el-avatar :size="100" :src="fsy"
                   shape="circle" class="avatar"></el-avatar>
        <!--提取后台管理员账号的信息-->
        <span style="color: #151414">神名代理人</span>
        <span class="el-tooltip">——·——</span>
         <el-tooltip placement="bottom">
           <span class="el-tooltip">一天是不良人，一辈子都是。</span>
           <template #content>
          <span>何人唤我瀛洲不良人！</span>
        </template>
      </el-tooltip>

      </div>

      <!-- 第二部分：占 10%，从左到右边分别记录文章数，分类数，评论数 -->
      <div class="part-two">
        <span>{{ articleCount }}</span>
        <span>{{ categoryCount }}</span>
        <span>{{ commentCount }}</span>
      </div>
      <!-- 第三部分：占 10%，空着 -->
      <div class="part-three">
        <span>文章数</span>
        <span>分类数</span>
        <span>评论数</span>
      </div>
      <!-- 第四部分：占 10%，空着 -->
      <div class="part-four">
        <div class="part-four-icon" style="color: #139fdc;">
          <i class="fa-brands fa-bilibili" @click="goToBilibili" ></i>
        </div>
        <div class="part-four-icon">
          <i class="fa-brands fa-github" @click="goToGitHub"></i>
        </div>
      </div>
    </div>
    <!-- 其他小组件，如公告栏、电子时钟等 -->
    <div class="notice-board" :class="{ 'fade-in-up': isNoticeBoardVisible }">
      <!-- 公告栏 -->
      <div class="notice-title">
        <i class="fa-solid fa-bullhorn" style="font-size: 22px;">公告栏</i>
      </div>
      <div class="notice-content">快来开启你的二次元之旅，探索你的世界吧!~！</div>
    </div>
    <!-- 电子时钟 -->
    <div class="clock" :class="{ 'fade-in-up': isClockVisible }">
      <Time/>
    </div>
    <!--最新文章组件-->
    <div class="newest-article">
      <NewArticle/>
    </div>

  </div>
</template>

<style scoped lang="scss">
.avatar-container {
  display: flex;
  flex-direction: column;
  align-items: center;

  .square-frame {
    width: 100%;
    height: 350px;
    border: 2px solid #ccc;
    border-radius: 15px;
    display: flex;
    flex-direction: column; /* 让子元素垂直排列 */
    margin-bottom: 20px;
    background-color: var(--bg);
    opacity: 0;
    transform: translateY(20px);
    transition: all 0.5s ease-in-out;

    &.fade-in-up {
      opacity: 1;
      transform: translateY(0);
    }

    .part-one {
      border-top-left-radius: 15px;
      border-top-right-radius: 15px;
      flex: 0.7;
      /*背景图片*/
      background-image: url("@/assets/【哲风壁纸】动漫-动漫壁纸-夜景.png");
      background-size: cover;
      background-repeat: no-repeat;
      display: flex;
      justify-content: center;
      align-items: center;
      color: var(--bg);
      flex-direction: column; /* 让子元素垂直排列 */
      overflow: hidden;
      .avatar {
        /*鼠标悬浮时*/
        &:hover {
          transform: scale(1.1) rotate(360deg);
          transition: transform 0.3s ease-in-out;
          cursor: pointer;
        }
        &:active {
          transform: scale(0.9);
          transition: transform 0.3s ease-in-out;
          cursor: pointer;
        }
      }

      .el-tooltip{
        display: flex;
        justify-content: center;
        align-items: center;
        font-size: 16px;
        color: #c3bfbf;;
        &:hover {
          cursor: pointer;
          transform: scale(1.1);
          color: #ffffff;
        }
      }
    }

    .part-two {
      flex: 0.125;
      display: flex;
      justify-content: space-around;
      align-items: center;
    }

    .part-three {
      flex: 0.125;
      display: flex;
      justify-content: space-around;
      align-items: center;
    }

    .part-four {
      flex: 0.15;
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 4px;
      border-top: 1px solid #ccc;
      overflow: hidden;
      .part-four-icon  {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background-color: rgba(170, 163, 170, 0.56);
        display: flex;
        justify-content: center;
        align-items: center;
        font-size: 24px;
        cursor: pointer;
        &:first-child {
          margin-right: 10px;
        }
        &:hover {
          transform: scale(1.1);
          transition: transform 0.3s ease-in-out;
          cursor: pointer;
        }

      }
    }
  }

  .notice-board {
    width: 100%;
    height: 125px;
    display: flex;
    flex-direction: column;
    align-items: center;
    border: 2px solid #ccc;
    border-radius: 15px;
    margin-bottom: 20px;
    background-color: var(--bg);
    opacity: 0;
    transform: translateY(20px);
    transition: all 0.5s ease-in-out;
    background-image: var(--notice-board-bg);
    background-size: cover;
    background-repeat: no-repeat;;

    &.fade-in-up {
      opacity: 1;
      transform: translateY(0);
      &:hover {
        cursor: pointer;
        box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
      }

    }

    .notice-title {
      font-size: 25px;
      font-weight: bold;
      padding: 5px 0;
      margin: 0 auto;
    }

    .notice-content {
      display: flex;
      justify-content: center;
      width: 70%;
      text-align: center;
      overflow: hidden;
    }
  }

  .clock {
    width: 100%;
    height: 125px;
    display: flex;
    flex-direction: column;
    align-items: center;
    border: 2px solid #ccc;
    border-radius: 15px;
    background-color: var(--bg);
    opacity: 0;
    overflow: hidden;
    transform: translateY(20px);
    transition: all 0.5s ease-in-out;
    margin-bottom: 20px;

    &.fade-in-up {
      opacity: 1;
      transform: translateY(0);
      &:hover {
        cursor: pointer;
        box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
      }

    }
  }

  .newest-article {
    width: 100%;

  }

}
</style>