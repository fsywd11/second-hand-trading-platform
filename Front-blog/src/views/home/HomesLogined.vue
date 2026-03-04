<script setup lang="js">
import { ref, watch, onMounted } from 'vue';
import {
  User, ShoppingBag, Star, Setting, Connection,
} from '@element-plus/icons-vue';
import { userInfoServices,} from "@/api/user.js";
import useUserInfoStore from "@/stores/userInfo.js";
import { useTokenStore } from '@/stores/token.js';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter, useRoute } from 'vue-router';
import Footer from "@/components/footer.vue";
const userInfoStore = useUserInfoStore();
const tokenStore = useTokenStore();
const router = useRouter();
const route = useRoute();

const isUserInfoLoaded = ref(false);
const imgUrl = ref('');

// 当前激活的菜单项
const activeMenu = ref('home');
// 修正路由监听逻辑，确保菜单激活状态精准匹配
watch(() => route.fullPath, (newFullPath) => {
  // 使用fullPath（包含query参数）进行匹配，避免参数变化时匹配失效
  if (newFullPath === '/homes/logined' || newFullPath === '/homes/mycenter') {
    activeMenu.value = 'home';
  } else if (newFullPath === '/homes/mygoods') {
    activeMenu.value = 'published';
  } else if (newFullPath.includes('/homes/myorder?status=sold')) {
    activeMenu.value = 'sold';
  } else if (newFullPath.includes('/homes/myorder?status=bought')) {
    activeMenu.value = 'bought';
  } else if (newFullPath === '/homes/mycollect') {
    activeMenu.value = 'collect';
  }
}, { immediate: true, flush: 'post' });

// 获取用户信息
const getUserInfo = async () => {
  try {
    let result = await userInfoServices();
    userInfoStore.setInfo(result.data);
    imgUrl.value = userInfoStore.info.userPic;
    isUserInfoLoaded.value = true;
  } catch (error) {
    console.error('获取用户信息失败:', error);
  }
}

// 组件挂载后再获取用户信息，避免初始渲染闪烁
onMounted(() => {
  getUserInfo();
});

function handleLogout() {
  ElMessageBox.confirm('确认要退出当前账号吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    tokenStore.removeToken();
    userInfoStore.removeInfo();
    await router.push('/');
    ElMessage.success('已安全退出');
  });
}

// 优化菜单点击事件：使用replace或push确保路由无刷新切换
const handleMenuClick = (index) => {
  let targetPath = '';
  switch (index) {
    case 'home':
      targetPath = '/homes/mycenter';
      break;
    case 'published':
      targetPath = '/homes/mygoods';
      break;
    case 'sold':
      targetPath = '/homes/mySold';
      break;
    case 'bought':
      targetPath = '/homes/myBought';
      break;
    case 'collect':
      targetPath = '/homes/mycollect';
      break;
    case 'profile':
      targetPath = '/homes/userinfo';
      break;
    case 'security':
      targetPath = '/homes/resetPassword';
      break;
    case 'logout':
      handleLogout();
      return; // 退出登录单独处理，不执行路由跳转
  }

  // 如果目标路由和当前路由一致，不重复跳转
  if (targetPath && route.fullPath !== targetPath) {
    // 使用push进行路由跳转（保留历史记录），而非replace
    router.push(targetPath).catch(err => {
      // 捕获重复跳转的错误，避免控制台告警
      if (err.name !== 'NavigationDuplicated') {
        console.error('路由跳转失败:', err);
      }
    });
  }
};
</script>

<template>
  <div class="xianyu-wrapper">
    <div class="xianyu-container">
      <aside class="xianyu-sidebar">
        <el-menu
            :model-value="activeMenu"
            @select="handleMenuClick"
            class="xianyu-menu"
            :default-active="activeMenu"
            :ellipsis="false"
        >
          <el-menu-item index="home">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </el-menu-item>

          <el-sub-menu index="trade">
            <template #title>
              <el-icon><ShoppingBag /></el-icon>
              <span>我的交易</span>
            </template>
            <el-menu-item index="published">我发布的</el-menu-item>
            <el-menu-item index="sold">我卖出的</el-menu-item>
            <el-menu-item index="bought">我买到的</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="collect">
            <el-icon><Star /></el-icon>
            <span>我的收藏</span>
          </el-menu-item>

          <el-sub-menu index="account">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>账户设置</span>
            </template>
            <el-menu-item index="profile">个人资料</el-menu-item>
            <el-menu-item index="security">账号与安全</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="logout" class="logout-item">
            <el-icon><Connection /></el-icon>
            <span>退出登录</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="xianyu-main">
        <router-view v-slot="{ Component, route }">
          <transition name="slide-fade" mode="out-in">
            <keep-alive>
              <component :is="Component" :key="route.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
  <Footer />
</template>

<style lang="scss" scoped>
/* 保留原有样式，新增过渡动画样式 */
.xianyu-wrapper {
  background-color: #f6f6f6;
  min-height: 100vh;
  padding: 20px 0;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.xianyu-container {
  width: 1500px;
  margin: 70px auto;
  display: flex;
  gap: 20px;
}

/* 左侧侧边栏样式 */
.xianyu-sidebar {
  width: 180px;
  background: #fff;
  border-radius: 12px;
  padding: 15px 0;
  height: fit-content;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);

  :deep(.xianyu-menu) {
    border-right: none;
    background: transparent;

    .el-menu-item {
      height: auto;
      padding: 12px 20px;
      line-height: normal;
      font-size: 14px;
      color: #333;
      margin: 0;
      border-left: 4px solid transparent;
      transition: all 0.3s ease;

      &:hover {
        background-color: #f8f8f8;
        color: #ffda00;
      }

      &.is-active {
        background: #f8f8f8;
        font-weight: bold;
        color: #ffda00;
        border-left-color: #ffda00;
      }

      .el-icon {
        font-size: 18px;
        color: #666;
        margin-right: 10px;
        transition: color 0.3s ease;
      }

      &:hover .el-icon,
      &.is-active .el-icon {
        color: #ffda00;
      }
    }

    .el-sub-menu {
      .el-sub-menu__title {
        height: auto;
        padding: 12px 20px;
        line-height: normal;
        font-size: 14px;
        color: #333;
        border-left: 4px solid transparent;
        transition: all 0.3s ease;

        &:hover {
          background-color: #f8f8f8;
          color: #ffda00;
        }

        .el-icon {
          font-size: 18px;
          color: #666;
          margin-right: 10px;
          transition: color 0.3s ease;
        }

        &:hover .el-icon {
          color: #ffda00;
        }

        .el-sub-menu__icon-arrow {
          right: 20px;
          color: #999;
          transition: transform 0.3s ease;
        }

        &.is-opened .el-sub-menu__icon-arrow {
          transform: rotate(180deg);
        }
      }

      .el-menu-item {
        padding: 8px 20px 8px 48px;
        font-size: 13px;
        color: #666;
        border-left: none;

        &:hover {
          color: #ffda00;
          background-color: #fafafa;
        }

        &.is-active {
          color: #ffda00;
          font-weight: 500;
          background-color: transparent;
          border-left: none;
        }
      }
    }

    .logout-item {
      color: #ff4d4f !important;
      margin-top: 20px;

      .el-icon {
        color: #ff4d4f !important;
      }

      &:hover {
        background-color: #fff2f0 !important;
        color: #ff4d4f !important;
      }
    }
  }
}

/* 主内容区样式 */
.xianyu-main {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  /* 确保过渡动画有足够的渲染空间 */
  min-height: 600px;
}

/* 路由过渡动画样式 */
:deep(.slide-fade-enter-from) {
  opacity: 0;
  transform: translateX(10px);
}

:deep(.slide-fade-enter-active) {
  transition: all 0.3s ease;
}

:deep(.slide-fade-leave-to) {
  opacity: 0;
  transform: translateX(-10px);
}

:deep(.slide-fade-leave-active) {
  transition: all 0.2s ease;
}

.avatar-dialog-inner {
  text-align: center;

  .inner-uploader {
    width: 120px;
    height: 120px;
    border: 2px dashed #ddd;
    border-radius: 50%;
    margin: 20px auto;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .uploader-placeholder {
      font-size: 30px;
      color: #999;
      margin-top: 40px;
    }
  }
}

/* 响应式适配 */
@media screen and (max-width: 1560px) {
  .xianyu-container {
    width: calc(100% - 40px);
  }
}
</style>