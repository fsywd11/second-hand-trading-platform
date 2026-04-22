<script setup lang="js">
import { computed } from 'vue';
import { buildTraceSnapshotSections } from '@/utils/traceSnapshot.js';

const props = defineProps({
  payloadJson: {
    type: String,
    default: ''
  }
});

const sections = computed(() => buildTraceSnapshotSections(props.payloadJson));
</script>

<template>
  <div class="trace-snapshot">
    <div
        v-for="section in sections"
        :key="section.title"
        class="trace-snapshot__section"
    >
      <div class="trace-snapshot__section-title">{{ section.title }}</div>

      <div v-if="section.type === 'cards'" class="trace-snapshot__cards">
        <div
            v-for="entry in section.entries"
            :key="entry.title"
            class="trace-snapshot__card"
        >
          <div class="trace-snapshot__card-title">{{ entry.title }}</div>
          <div class="trace-snapshot__grid">
            <div
                v-for="item in entry.items"
                :key="`${entry.title}-${item.key}`"
                class="trace-snapshot__item"
            >
              <label>{{ item.label }}</label>
              <div class="trace-snapshot__value" :class="{ 'is-code': item.kind === 'code' }">
                <template v-if="item.kind === 'image' && item.value !== '--'">
                  <div class="trace-snapshot__image-box">
                    <img :src="item.value" :alt="item.label" />
                    <a :href="item.value" target="_blank" rel="noreferrer">{{ item.value }}</a>
                  </div>
                </template>
                <template v-else>
                  {{ item.value }}
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="trace-snapshot__grid">
        <div
            v-for="item in section.items"
            :key="`${section.title}-${item.key}`"
            class="trace-snapshot__item"
        >
          <label>{{ item.label }}</label>
          <div class="trace-snapshot__value" :class="{ 'is-code': item.kind === 'code' }">
            <template v-if="item.kind === 'image' && item.value !== '--'">
              <div class="trace-snapshot__image-box">
                <img :src="item.value" :alt="item.label" />
                <a :href="item.value" target="_blank" rel="noreferrer">{{ item.value }}</a>
              </div>
            </template>
            <template v-else>
              {{ item.value }}
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.trace-snapshot {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 14px 14px;
}

.trace-snapshot__section {
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #edf2f7;
  overflow: hidden;
}

.trace-snapshot__section-title {
  padding: 12px 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #f4fff8 100%);
  border-bottom: 1px solid #edf2f7;
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

.trace-snapshot__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  padding: 14px;
}

.trace-snapshot__item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    color: #7b8794;
    font-size: 12px;
    line-height: 1.4;
  }
}

.trace-snapshot__value {
  color: #1f2937;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-all;

  &.is-code {
    font-family: "Consolas", "SFMono-Regular", monospace;
    color: #334155;
  }
}

.trace-snapshot__cards {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.trace-snapshot__card {
  border-radius: 12px;
  border: 1px solid #edf2f7;
  background: #fbfdff;
  overflow: hidden;
}

.trace-snapshot__card-title {
  padding: 10px 14px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.trace-snapshot__image-box {
  display: flex;
  flex-direction: column;
  gap: 8px;

  img {
    width: 88px;
    height: 88px;
    object-fit: cover;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
    background: #fff;
  }

  a {
    color: #0f8a5f;
    text-decoration: none;
    word-break: break-all;
  }
}

@media (max-width: 768px) {
  .trace-snapshot {
    padding: 0 12px 12px;
  }

  .trace-snapshot__grid {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 12px;
  }

  .trace-snapshot__cards {
    padding: 12px;
  }
}
</style>
