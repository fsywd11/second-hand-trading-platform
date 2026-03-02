<script setup lang="ts">
import {
  User,
  Location,
  SwitchButton,
  HomeFilled,
  ShoppingBag,
  Star,
  Message,
  List,
  Promotion,
  Plus,
  Bell, ShoppingTrolley
} from "@element-plus/icons-vue";
import { useTokenStore } from "@/stores/token.js";
import {ElMessage, ElMessageBox} from "element-plus";
import { useRouter } from "vue-router";

const emit = defineEmits(["update:closeDrawer"]);
const tokenStore = useTokenStore();
const router = useRouter();

// 关闭抽屉
function isClose() {
  emit("update:closeDrawer");
}

// 权限校验 + 跳转 + 关闭抽屉
const handleMenuClick = (index: string) => {
  // 允许免登录访问的路由白名单
  const whiteList = ['/homes/home', '/'];

  // 命中非白名单且没有 token 时拦截
  if (!whiteList.includes(index) && tokenStore.token === '') {
    ElMessage.warning('请登录');
    return;
  }

  // 鉴权通过，执行跳转并关闭抽屉
  router.push(index);
  isClose();
};

// 发布商品按钮点击处理
const handlePublishClick = () => {
  if (tokenStore.token === '') {
    ElMessage.warning('请登录');
    return;
  }
  router.push('/homes/publish');
  isClose();
};

// 退出登录处理
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('你确认要退出登录吗?', '温馨提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    });

    tokenStore.removeToken();
    // 如果有用户信息 store 也需要清空
    // userInfoStore.removeInfo();

    if ('caches' in window) {
      caches.keys().then((cacheNames) => {
        cacheNames.forEach((cacheName) => { caches.delete(cacheName) });
      });
    }

    await router.push('/');
    ElMessage.success('退出登录成功');
    isClose();
  } catch (error) {
    ElMessage.info('用户取消了退出登录');
  }
};
</script>

<template>
  <div class="mobile-menu-container">
    <el-menu
        router
        style="width: 100%; border: none; height: 100%;"
        class="menu no-select"
    >
      <!-- 基础导航 -->
      <el-menu-item index="/homes/home" @click="handleMenuClick('/homes/home')">
        <el-icon><HomeFilled /></el-icon>
        商品
      </el-menu-item>

      <!-- 需要登录的菜单 -->
      <el-menu-item index="/homes/mygoods" @click="handleMenuClick('/homes/mygoods')">
        <el-icon><ShoppingBag /></el-icon>
        我的商品
      </el-menu-item>

      <el-menu-item index="/homes/mycollect" @click="handleMenuClick('/homes/mycollect')">
        <el-icon><Star /></el-icon>
        我的收藏
      </el-menu-item>

      <el-menu-item index="/homes/myfoot" @click="handleMenuClick('/homes/myfoot')">
        <el-icon><Promotion /></el-icon>
        足迹
      </el-menu-item>

      <el-menu-item index="/homes/dynamic" @click="handleMenuClick('/homes/dynamic')">
        <el-icon><Message /></el-icon>
        用户动态
      </el-menu-item>

      <el-menu-item index="/orders" @click="handleMenuClick('/orders')">
        <el-icon><ShoppingTrolley /></el-icon>
        订单
      </el-menu-item>

      <el-menu-item index="/notice" @click="handleMenuClick('/notice')">
        <el-icon><Bell /></el-icon>
        通知
      </el-menu-item>

      <el-menu-item index="/homes/mydynamic" @click="handleMenuClick('/homes/mydynamic')">
        <el-icon><List /></el-icon>
        我的动态
      </el-menu-item>

      <!-- 发布商品按钮 -->
      <el-menu-item class="publish-item" @click="handlePublishClick">
        <el-icon><Plus /></el-icon>
        发布商品
      </el-menu-item>

      <!-- 用户中心相关 -->
      <el-sub-menu index="user" v-if="tokenStore.token">
        <template #title>
          <el-icon><User /></el-icon>
          个人中心
        </template>
        <el-menu-item index="/homes/logined" @click="handleMenuClick('/homes/logined')">
          <el-icon><User /></el-icon>
          个人主页
        </el-menu-item>
        <el-menu-item index="/homes/address" @click="handleMenuClick('/homes/address')">
          <el-icon><Location /></el-icon>
          收货地址
        </el-menu-item>
        <el-menu-item @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-menu-item>
      </el-sub-menu>

      <!-- 登录入口 -->
      <el-menu-item index="/homes/login" v-else @click="handleMenuClick('/homes/login')">
        <el-icon><User /></el-icon>
        登录
      </el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped lang="scss">
.mobile-menu-container {
  height: 100%;
  padding-top: 10px;
}

.menu {
  --el-menu-text-color: #303133;
  --el-menu-active-text-color: #ff5e78;
  --el-menu-hover-text-color: #ff5e78;

  .el-menu-item {
    height: 50px;
    line-height: 50px;
    margin: 0 10px;
    border-radius: 8px;
    margin-bottom: 8px;

    &:hover {
      background-color: #f5f5f5;
    }
  }

  // 发布商品按钮特殊样式
  .publish-item {
    background-color: #ff5e78 !important;
    color: white !important;

    &:hover {
      background-color: #ff4765 !important;
    }

    .el-icon {
      color: white !important;
    }
  }

  .el-sub-menu {
    .el-sub-menu__title {
      height: 50px;
      line-height: 50px;
      margin: 0 10px;
      border-radius: 8px;
      margin-bottom: 8px;

      &:hover {
        background-color: #f5f5f5;
      }
    }

    .el-menu-item {
      margin-left: 20px;
    }
  }
}
</style>