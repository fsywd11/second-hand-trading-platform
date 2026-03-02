<script lang="js" setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import {
  User,
  SwitchButton,
  Close,
  Operation,
  Location,
  Search, Plus, HomeFilled, Bell, Tickets // 引入搜索图标
} from '@element-plus/icons-vue';
import userlogin from "@/assets/default.png"
import { useRouter, useRoute } from 'vue-router';
import { useTokenStore } from "@/stores/token.js"
import useUserInfoStore from "@/stores/userInfo.js";
import { ElMessage, ElMessageBox, ElTooltip } from "element-plus";
import { userInfoServices } from "@/api/user.js";
import MoveMenu from "@/components/MoveMenu.vue";
import SvgIcon from "@/components/SvgIcon.vue";
import Loading from '@/components/Loading.vue' // 确保路径正确
import { useLoadingStore } from '@/stores/loading'

const route = useRoute();
const activeMenu = ref(route.path)
const userInfoStore = useUserInfoStore();
const tokenStore = useTokenStore();
const mobileMenuVisible = ref(false);

// 搜索对话框控制
const searchDialogVisible = ref(false);
const searchQuery = ref('');

const getUserInfo = async () => {
  try {
    if (tokenStore) {
      let result = await userInfoServices();
      userInfoStore.setInfo(result.data);
    }
  } catch (error) {
    console.error('获取用户信息失败:', error);
  }
}

onMounted(() => {
  if (tokenStore.token === '') return;
  getUserInfo();
})

const router = useRouter();
const lastScrollTop = ref(0);
const isScrollingDown = ref(false);

const handleScroll = () => {
  const scrollTop = window.scrollY || document.documentElement.scrollTop;
  isScrollingDown.value = scrollTop > lastScrollTop.value && scrollTop > 56;
  lastScrollTop.value = scrollTop;
};

onMounted(() => {
  window.addEventListener('scroll', handlePageScroll);
  window.addEventListener('scroll', handleScroll);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handlePageScroll);
  window.removeEventListener('scroll', handleScroll);
});

const handleCommand = (command) => {
  if (command === 'logout') {
    ElMessageBox.confirm('你确认要退出登录吗?', '温馨提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
        .then(async () => {
          tokenStore.removeToken()
          userInfoStore.removeInfo()
          if ('caches' in window) {
            caches.keys().then((cacheNames) => {
              cacheNames.forEach((cacheName) => { caches.delete(cacheName) })
            })
          }
          await router.push('/')
          ElMessage.success('退出登录成功')
        })
        .catch(() => {
          ElMessage.info('用户取消了退出登录')
        })
  } else if(command=== 'logined') {
    // 拼接完整的路由地址，window.location.origin 获取当前域名（如 http://localhost:5173）
    const targetUrl = `${window.location.origin}/homes/${command}`;
    // 打开新标签页
    window.open(targetUrl, '_blank');
  } else {
    router.push('/homes/' + command)
  }
}

const isShow = ref(true)
let timer = null
onMounted(() => {
  timer = setTimeout(() => { isShow.value = false }, 4000)
})
onUnmounted(() => { clearTimeout(timer) })

const isMobile = ref(window.innerWidth <= 1200);
const drawer = ref(false);
watch(() => window.innerWidth, (newWidth) => {
  isMobile.value = newWidth <= 1200;
  if (!isMobile.value) mobileMenuVisible.value = false;
});

const showBackTop = ref(false);
const handlePageScroll = () => {
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
  showBackTop.value = scrollTop > 300;
};

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

// 1. 定义搜索相关的响应式数据
const searchResults = ref([]); // 存储搜索结果列表
const isSearching = ref(false); // 加载状态
const hasSearched = ref(false); // 新增：标记是否执行过搜索动作
// 2. 搜索执行函数
const pageNum = ref(1); // 当前页码
const total = ref(0);   // 总条数，用于判断是否还有更多

// 修改后的搜索执行函数
const handleSearch = async () => {
  if (!searchQuery.value.trim()) return;

  isSearching.value = true;
  hasSearched.value = true;
  pageNum.value = 1; // 重新搜索时重置页码

  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: 10,
      keyword: searchQuery.value
    };
    const result = await articleSearch(params);
    searchResults.value = result.data.items || [];
    total.value = result.data.total; // 假设后端返回了总数 total
  } catch (error) {
    console.error('搜索失败:', error);
    searchResults.value = [];
  } finally {
    isSearching.value = false;
  }
};

watch(searchDialogVisible, (val) => {
  if (!val) {
    searchQuery.value = '';
    searchResults.value = [];
    hasSearched.value = false;
    total.value = 0; // 重置
  }
});

// 初始化加载状态 store
const loadingStore = useLoadingStore()
// 监听路由变化控制加载状态
watch(
    () => router.currentRoute.value,
    (newRoute, oldRoute) => {
      // 路由开始切换时显示加载
      if (newRoute.path !== oldRoute?.path) {
        loadingStore.setLoading(true)
      }

      // 延迟隐藏加载（确保组件渲染完成）
      const timer = setTimeout(() => {
        loadingStore.setLoading(false)
        clearTimeout(timer)
      }, 300)
    },
    { immediate: true }
)

// ======================== 新增权限校验逻辑 ========================
// 拦截菜单栏点击
const handleMenuSelect = (index) => {
  // 允许免登录访问的路由白名单
  const whiteList = ['/homes/home', '/','/homes/dynamic'];

  // 命中非白名单且没有 token
  if (!whiteList.includes(index) && tokenStore.token === '') {
    ElMessage.warning('请登录');
    // 可选：拦截后直接跳转登录页 -> router.push('/homes/login')
    return;
  }
  if(index==='order'){
    // 拼接完整的路由地址，window.location.origin 获取当前域名（如 http://localhost:5173）
    const targetUrl = `${window.location.origin}/homes/myorder`;
    // 打开新标签页
    window.open(targetUrl, '_blank');
    return;
  }

  // 鉴权通过，执行跳转
  router.push(index);
};

// 拦截“发布商品”按钮点击
const handlePublishClick = () => {
  if (tokenStore.token === '') {
    ElMessage.warning('请登录');
    return;
  }
  router.push('/homes/publish');
};
</script>

<template>
  <el-container class="layout-container">
    <div class="common-layout">
      <el-menu
          class="nav-bar no-select"
          mode="horizontal"
          :style="{ transform: isScrollingDown ? 'translateY(-100%)' : 'translateY(0)' }"
          :default-active="activeMenu"
          :ellipsis="false"
          @select="handleMenuSelect"
      >
        <span class="menu-icon" @click="drawer = !drawer">
          <el-icon><Operation /></el-icon>
        </span>

        <div class="nav-left">
          <el-tooltip placement="bottom" content="点击返回首页">
            <div class="logo" @click="router.push('/')">
              <SvgIcon icon-class="tag" style="width: 20px; height: 20px; margin-right: 5px;"/> 校园交易
            </div>
          </el-tooltip>

          <el-menu-item index="/homes/home"><el-icon><HomeFilled /></el-icon>商品</el-menu-item>
        </div>

        <div class="nav-center">
          <el-input
              class="search-input"
              placeholder="搜索商品..."
              v-model="searchQuery"
              clearable
              @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="nav-right">
          <el-menu-item index="order"><el-icon><Tickets /></el-icon>订单</el-menu-item>
          <el-menu-item index="/homes/notice"><el-icon><Bell /></el-icon>消息</el-menu-item>

          <div class="publish-btn" @click="handlePublishClick">
            <el-icon><Plus /></el-icon> 发布商品
          </div>

          <el-dropdown placement="bottom-end" @command="handleCommand">
            <div class="user-avatar-wrapper" @click="tokenStore.token === '' ? router.push('/homes/login') : null">
              <el-avatar :size="36" :src="userInfoStore.info.userPic ? userInfoStore.info.userPic : userlogin"/>
            </div>
            <template v-if="tokenStore.token !== ''" #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logined" :icon="User">个人主页</el-dropdown-item>
                <el-dropdown-item command="address" :icon="Location">收货地址</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-menu>

      <el-main style="padding: 0;margin: 0;">
        <Loading />
        <router-view v-slot="{ Component, loading }">
          <template v-if="loading">
            <Loading />
          </template>
          <template v-else>
            <keep-alive>
              <component :is="Component" />
            </keep-alive>
          </template>
        </router-view>
      </el-main>
    </div>
  </el-container>

  <el-drawer v-model="drawer" :with-header="true" size="50%" direction="ltr" :show-close="false" class="drawer-panel">
    <template #header>
      <span style="font-size: 1.2rem">导航</span>
      <el-button :icon="Close" style="background: none;font-size: 1.5rem;width: 30px;border: none" @click="drawer = false"/>
    </template>
    <template #default>
      <MoveMenu @update:closeDrawer="drawer = false"/>
    </template>
  </el-drawer>

  <div v-if="showBackTop" class="custom-backtop no-select" @click="scrollToTop">
    <SvgIcon icon-class="icon-xiangsufeng_huojian" style="width: 40px; height: 40px;" />
  </div>
</template>

<style scoped lang="scss">
/* 样式部分保持原有内容，无任何修改 */
// 基础样式
.custom-backtop {
  position: fixed;
  right: 30px;
  bottom: 30px;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1000;
  transition: all 0.3s ease;
  &:hover { transform: translateY(-2px); }
}

.common-layout { height: 100%; width: 100%; }

/* --- 核心导航栏重构样式 --- */
.nav-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--nav-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
  border-right: none; // 移除默认边框
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 56px;
  padding: 0 20px;
  z-index: 1000;
  transition: transform 0.3s ease;

  .menu-icon { display: none; }

  // 左侧区：禁止挤压
  .nav-left {
    display: flex;
    align-items: center;
    flex-shrink: 0;

    .logo {
      display: flex;
      align-items: center;
      font-size: 18px;
      font-weight: bold;
      cursor: pointer;
      color: var(--text);
      margin-right: 20px;
      white-space: nowrap;
    }
  }

  // 中间区：搜索框自适应宽度
  .nav-center {
    flex-grow: 1;
    display: flex;
    justify-content: center;
    padding: 0 20px;

    .search-input {
      max-width: 400px; // 防止过长
      width: 100%;
    }
  }

  // 右侧区：禁止挤压
  .nav-right {
    display: flex;
    align-items: center;
    flex-shrink: 0;
    gap: 15px; // 调整各元素间距
  }

  /* 修复菜单项悬浮背景样式并保持动画 */
  :deep(.el-menu-item) {
    height: 56px;
    line-height: 56px;
    padding: 0 15px;
    border-bottom: none !important; // 取消Element自带的底部边框
    background-color: transparent !important;
    position: relative;
    color: var(--text) !important; // 维持文字颜色

    // 底部线条动画
    &::after {
      content: '';
      position: absolute;
      bottom: 0; left: 50%;
      width: 0; height: 2px;
      background-color: #ff5d7d; // 你想要的主题色/强调色
      transition: all 0.3s ease;
      transform: translateX(-50%);
    }

    &:hover, &.is-active {
      background-color: transparent !important; // 确保背景完全透明
    }

    &:hover::after, &.is-active::after {
      width: 80%; // 悬浮或激活时线条伸展
    }
  }

  // 发布商品按钮专属样式（从截图提取）
  .publish-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #ff5e78; // 粉红背景
    color: white;
    padding: 0 16px;
    height: 36px;
    border-radius: 18px; // 胶囊状
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.3s ease;
    white-space: nowrap;

    .el-icon {
      margin-right: 4px;
    }

    &:hover {
      background-color: #ff4765;
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(255, 94, 120, 0.3);
    }
  }

  .user-avatar-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 2px;
    border-radius: 50%;
    cursor: pointer;
    transition: all 0.3s ease;
    border: 2px solid transparent;

    &:hover {
      border-color: #ff5e78;
      transform: translateY(-2px);
    }

    .el-avatar {
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
  }

  // 移动端适配
  @media screen and (max-width: 1200px) {
    padding: 0 10px;
    .nav-left .el-menu-item,
    .nav-center,
    .nav-right .el-menu-item {
      display: none !important; // 隐藏文字链接和搜索框
    }
    .menu-icon { display: block; font-size: 24px; margin-right: 15px; cursor: pointer; color: var(--text); }
  }
}

// 搜索对话框样式
:deep(.custom-search-dialog) {
  border-radius: 16px;
  overflow: hidden;
  .el-dialog__header {
    margin-right: 0;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .el-input__wrapper {
    box-shadow: 0 0 0 1px var(--el-border-color) inset;
    &:hover { box-shadow: 0 0 0 1px var(--accent) inset; }
    &.is-focus { box-shadow: 0 0 0 1px var(--accent) inset !important; }
  }
  @media screen and (max-width: 768px) {
    .el-dialog__body {
      padding: 15px 12px;
    }
  }
  .el-input__wrapper input {
    font-size: 16px;
  }
}

// 移除 Element 默认焦点效果
.el-menu--horizontal .el-menu-item:not(.is-disabled):focus,
.el-menu--horizontal .el-menu-item:not(.is-disabled):hover {
  background-color: transparent !important;
  outline: none;
}

// 搜索结果列表...
.search-results-container {
  margin-top: 20px;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 5px;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: var(--el-border-color-lighter); border-radius: 10px; }

  .result-list { display: flex; flex-direction: column; gap: 12px; }

  .result-item {
    display: flex; gap: 15px; padding: 10px; border-radius: 8px; cursor: pointer;
    transition: background 0.2s ease; border: 1px solid transparent;
    &:hover { background-color: var(--el-fill-color-light); border-color: var(--accent); }
    .result-cover { width: 80px; height: 60px; object-fit: cover; border-radius: 4px; }
    .result-info {
      flex: 1; display: flex; flex-direction: column; justify-content: space-between;
      .result-title { font-weight: bold; font-size: 14px; color: var(--text); }
      .result-meta { font-size: 12px; color: var(--el-text-color-secondary); display: flex; gap: 15px; align-items: center; }
    }
  }
}

.load-more-wrapper {
  text-align: center; padding: 15px 0;
  .el-button { color: var(--accent); font-size: 13px; }
}

.no-more-tips {
  text-align: center; padding: 15px 0; font-size: 12px; color: var(--el-text-color-secondary); opacity: 0.7;
}
</style>