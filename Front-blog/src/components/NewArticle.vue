<script setup lang="js">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import ArticleInfo from "@/stores/ArticleInfo.js";

// 初始化变量
const articleInformation = ArticleInfo();
const router = useRouter();
const recentArticles = ref([]);
const loading = ref(true);
const isVisible = ref(false);

// 格式化日期
const formatDate = (dateString) => {
  return dateString ? new Date(dateString).toLocaleDateString() : '';
};

// 获取并处理最新3篇文章
const fetchRecentArticles = () => {
  try {
    loading.value = true;
    // 从存储中获取文章并按时间排序，取最新3篇
    recentArticles.value = [...(articleInformation.info || [])]
        .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
        .slice(0, 3);
  } catch (error) {
    console.error('获取最新文章失败:', error);
  } finally {
    loading.value = false;
  }
};

// 跳转到文章详情
const goToArticleDetail = (id) => {
  router.push(`/homes/articleComment/${id}`);
};

// 组件挂载时执行
onMounted(() => {
  fetchRecentArticles();

  // 滚动渐显效果
  const observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      isVisible.value = true;
      observer.disconnect();
    }
  });

  observer.observe(document.querySelector('.recent-articles'));
});
</script>


<template>
  <div class="recent-articles" :class="{ 'fade-in-up': isVisible }">
    <div class="articles-title">
      <i class="fa-solid fa-newspaper"></i>
      <span>最新文章</span>
    </div>

    <div class="articles-list">
      <!-- 只显示最新的3篇文章 -->
      <div
          v-for="article in recentArticles"
          :key="article.id"
          class="article-item"
          @click="goToArticleDetail(article.id)"
      >
        <div class="article-title">
          <i class="fa-solid fa-angle-right"></i>
          <span>{{ article.title }}</span>
        </div>
        <div class="article-meta">
          <span class="category">{{ article.createUser }}</span>
          <span class="date">{{ formatDate(article.createTime) }}</span>
        </div>
      </div>

      <div v-if="loading" class="loading">
        <i class="fa-solid fa-spinner fa-spin"></i>
        <span>加载中...</span>
      </div>
      <div v-if="!loading && recentArticles.length === 0" class="no-data">
        <i class="fa-solid fa-file-alt"></i>
        <span>暂无文章</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.recent-articles {
  width: 100%;
  border: 2px solid #e0e0e0;
  border-radius: 15px;
  padding: 15px;
  margin-bottom: 20px;
  background-color: var(--bg);
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.5s ease-out;

  &.fade-in-up {
    opacity: 1;
    transform: translateY(0);
  }

  .articles-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 20px;
    font-weight: 600;
    padding: 8px 0 12px;
    margin-bottom: 15px;
    border-bottom: 1px solid #f0f0f0;
    color:var(--text);

    i {
      color: #139fdc;
      font-size: 22px;
    }
  }

  .articles-list {
    .article-item {
      padding: 12px 8px;
      border-bottom: 1px dashed #f0f0f0;
      cursor: pointer;
      transition: background-color 0.3s ease;

      &:last-child {
        border-bottom: none;
      }

      &:hover {
        border-radius: 6px;
      }

      .article-title {
        display: flex;
        align-items: center;
        margin-bottom: 6px;

        i {
          color: #139fdc;
          margin-right: 8px;
          font-size: 14px;
        }

        span {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          flex: 1;
          transition: color 0.3s ease;

          &:hover {
            color: #139fdc;
          }
        }
      }

      .article-meta {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
        color: #888;

        .category {
          background-color: #f0f7ff;
          padding: 2px 8px;
          border-radius: 12px;
          color: #139fdc;
        }
      }
    }

    .loading, .no-data {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 25px 0;
      color: #888;
    }
  }
}
</style>
