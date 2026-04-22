const ORDER_STATUS_MAP = {
  1: '待付款',
  2: '待发货',
  3: '待收货',
  4: '已完成',
  5: '已取消'
};

const GOODS_STATUS_MAP = {
  1: '在售',
  2: '已售罄',
  3: '已下架',
  4: '审核中',
  5: '违规封禁'
};

const GOODS_IS_NEW_MAP = {
  0: '二手',
  1: '全新',
  2: '9成新',
  3: '8成新',
  4: '7成新及以下'
};

const PAY_TYPE_MAP = {
  0: '未支付',
  1: '微信',
  2: '支付宝'
};

const REFUND_STATUS_MAP = {
  0: '无退款',
  1: '退款中',
  2: '退款成功',
  3: '退款失败'
};

const TRADE_PHASE_MAP = {
  CREATED: '订单已创建',
  PAID: '已支付待发货',
  DELIVERING: '已发货待收货',
  COMPLETED: '交易已完成',
  CANCELED: '交易已取消',
  UNKNOWN: '未知阶段'
};

const OWNERSHIP_STATUS_MAP = {
  TRANSFERRED: '权属已转移',
  SELLER_HOLDING: '卖家持有中'
};

const FIELD_META = {
  goodsId: { label: '商品ID' },
  goodsName: { label: '商品名称' },
  goodsDesc: { label: '商品描述' },
  goodsPic: { label: '商品主图', kind: 'image' },
  categoryId: { label: '分类ID' },
  originalPrice: { label: '原价', formatter: 'price' },
  sellPrice: { label: '售价', formatter: 'price' },
  sellerId: { label: '卖家ID' },
  goodsStatus: { label: '商品状态', formatter: 'goodsStatus' },
  isNew: { label: '新旧程度', formatter: 'goodsIsNew' },
  stock: { label: '库存数量' },
  orderId: { label: '订单ID' },
  orderNo: { label: '订单编号', kind: 'code' },
  buyerId: { label: '买家ID' },
  addressId: { label: '收货地址ID' },
  goodsTraceId: { label: '商品链上编号', kind: 'code' },
  orderTraceId: { label: '订单链上编号', kind: 'code' },
  goodsPrice: { label: '商品单价', formatter: 'price' },
  goodsNum: { label: '购买数量' },
  totalAmount: { label: '订单总金额', formatter: 'price' },
  orderStatus: { label: '订单状态', formatter: 'orderStatus' },
  tradePhase: { label: '交易阶段', formatter: 'tradePhase' },
  payType: { label: '支付方式', formatter: 'payType' },
  refundStatus: { label: '退款状态', formatter: 'refundStatus' },
  refundAmount: { label: '退款金额', formatter: 'price' },
  refundReason: { label: '退款原因' },
  refundRemark: { label: '退款备注' },
  remark: { label: '备注说明' },
  createTime: { label: '创建时间', formatter: 'time' },
  updateTime: { label: '更新时间', formatter: 'time' },
  payTime: { label: '支付时间', formatter: 'time' },
  deliveryTime: { label: '发货时间', formatter: 'time' },
  receiveTime: { label: '收货时间', formatter: 'time' },
  cancelTime: { label: '取消时间', formatter: 'time' },
  refundTime: { label: '退款时间', formatter: 'time' },
  ownershipStatus: { label: '权属状态', formatter: 'ownershipStatus' },
  previousOwnerId: { label: '原权属人ID' },
  currentOwnerId: { label: '当前权属人ID' },
  pendingOwnerId: { label: '待确认权属人ID' },
  transferOrderNo: { label: '权属变更订单号', kind: 'code' },
  transferTime: { label: '权属变更时间', formatter: 'time' },
  id: { label: '图片ID' },
  imageUrl: { label: '图片地址', kind: 'image' },
  extInfo: { label: '图片说明' }
};

const ROOT_GOODS_KEYS = [
  'goodsId',
  'goodsName',
  'goodsDesc',
  'goodsPic',
  'categoryId',
  'originalPrice',
  'sellPrice',
  'sellerId',
  'goodsStatus',
  'isNew',
  'stock'
];

const ROOT_ORDER_KEYS = [
  'orderId',
  'orderNo',
  'buyerId',
  'sellerId',
  'addressId',
  'goodsTraceId',
  'orderTraceId',
  'goodsPrice',
  'goodsNum',
  'totalAmount',
  'orderStatus',
  'tradePhase',
  'payType',
  'refundStatus',
  'refundAmount',
  'refundReason',
  'refundRemark',
  'remark'
];

const ROOT_TIME_KEYS = [
  'createTime',
  'updateTime',
  'payTime',
  'deliveryTime',
  'receiveTime',
  'cancelTime',
  'refundTime',
  'transferTime'
];

const LATEST_ORDER_KEYS = [
  'orderTraceId',
  'orderId',
  'orderNo',
  'buyerId',
  'sellerId',
  'goodsNum',
  'goodsPrice',
  'totalAmount',
  'orderStatus',
  'tradePhase',
  'payType',
  'refundStatus',
  'refundAmount',
  'refundReason',
  'payTime',
  'deliveryTime',
  'receiveTime',
  'cancelTime',
  'refundTime',
  'updateTime'
];

const OWNERSHIP_KEYS = [
  'ownershipStatus',
  'previousOwnerId',
  'currentOwnerId',
  'pendingOwnerId',
  'transferOrderNo',
  'transferTime'
];

function normalizeValue(value) {
  if (value === null || value === undefined || value === '') {
    return '--';
  }
  if (Array.isArray(value)) {
    return value.join('、') || '--';
  }
  return String(value);
}

function formatTimeValue(value) {
  const normalized = normalizeValue(value);
  if (normalized === '--') {
    return normalized;
  }
  return normalized.replace('T', ' ');
}

function formatPriceValue(value) {
  const normalized = normalizeValue(value);
  if (normalized === '--') {
    return normalized;
  }
  const numeric = Number(normalized);
  return Number.isNaN(numeric) ? normalized : `¥${numeric.toFixed(2)}`;
}

function looksLikeUrl(value) {
  return typeof value === 'string' && /^https?:\/\//i.test(value);
}

function formatByMeta(key, value) {
  const meta = FIELD_META[key] || {};
  const normalized = normalizeValue(value);
  if (normalized === '--') {
    return normalized;
  }

  switch (meta.formatter) {
    case 'time':
      return formatTimeValue(normalized);
    case 'price':
      return formatPriceValue(normalized);
    case 'orderStatus':
      return ORDER_STATUS_MAP[normalized] || normalized;
    case 'goodsStatus':
      return GOODS_STATUS_MAP[normalized] || normalized;
    case 'goodsIsNew':
      return GOODS_IS_NEW_MAP[normalized] || normalized;
    case 'payType':
      return PAY_TYPE_MAP[normalized] || normalized;
    case 'refundStatus':
      return REFUND_STATUS_MAP[normalized] || normalized;
    case 'tradePhase':
      return TRADE_PHASE_MAP[normalized] || normalized;
    case 'ownershipStatus':
      return OWNERSHIP_STATUS_MAP[normalized] || normalized;
    default:
      return normalized;
  }
}

function toDisplayItem(key, value) {
  const meta = FIELD_META[key] || {};
  const normalizedValue = formatByMeta(key, value);
  const kind = meta.kind || (looksLikeUrl(value) ? 'image' : 'text');
  const isCode = kind === 'code' || /hash|trace/i.test(key);
  return {
    key,
    label: meta.label || key,
    value: normalizedValue,
    kind: isCode ? 'code' : kind
  };
}

function buildSection(title, source, keys, consumedKeys) {
  if (!source || typeof source !== 'object') {
    return null;
  }
  const items = keys
    .filter((key) => !consumedKeys || !consumedKeys.has(key))
    .filter((key) => source[key] !== undefined && source[key] !== null && source[key] !== '')
    .map((key) => {
      if (consumedKeys) {
        consumedKeys.add(key);
      }
      return toDisplayItem(key, source[key]);
    });
  if (!items.length) {
    return null;
  }
  return {
    title,
    type: 'grid',
    items
  };
}

function buildImagesSection(images) {
  if (!Array.isArray(images) || !images.length) {
    return null;
  }
  return {
    title: '图片存证',
    type: 'cards',
    entries: images.map((image, index) => ({
      title: `凭证图片 ${index + 1}`,
      items: ['id', 'imageUrl', 'extInfo']
        .filter((key) => image?.[key] !== undefined && image?.[key] !== null && image?.[key] !== '')
        .map((key) => toDisplayItem(key, image[key]))
    })).filter((entry) => entry.items.length)
  };
}

function buildRemainingSection(source, consumedKeys) {
  const items = Object.keys(source || {})
    .filter((key) => !consumedKeys.has(key))
    .filter((key) => source[key] !== undefined && source[key] !== null && source[key] !== '')
    .filter((key) => !Array.isArray(source[key]) && typeof source[key] !== 'object')
    .map((key) => toDisplayItem(key, source[key]));

  if (!items.length) {
    return null;
  }

  return {
    title: '其他上链字段',
    type: 'grid',
    items
  };
}

function fallbackSection(payloadJson) {
  return [{
    title: '原始链上快照',
    type: 'grid',
    items: [{
      key: 'rawPayload',
      label: '原始内容',
      value: normalizeValue(payloadJson),
      kind: 'code'
    }]
  }];
}

export function buildTraceSnapshotSections(payloadJson) {
  if (!payloadJson) {
    return [];
  }

  let data;
  try {
    data = JSON.parse(payloadJson);
  } catch (error) {
    return fallbackSection(payloadJson);
  }

  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return fallbackSection(payloadJson);
  }

  const sections = [];
  const consumedKeys = new Set(['latestOrderEvidence', 'ownershipEvidence', 'images']);

  const goodsSection = buildSection('商品信息', data, ROOT_GOODS_KEYS, consumedKeys);
  const orderSection = buildSection('订单信息', data, ROOT_ORDER_KEYS, consumedKeys);
  const timeSection = buildSection('时间记录', data, ROOT_TIME_KEYS, consumedKeys);
  const latestOrderSection = buildSection('最新交易凭证', data.latestOrderEvidence, LATEST_ORDER_KEYS);
  const ownershipSection = buildSection('权属流转凭证', data.ownershipEvidence, OWNERSHIP_KEYS);
  const imageSection = buildImagesSection(data.images);
  const remainingSection = buildRemainingSection(data, consumedKeys);

  for (const section of [goodsSection, orderSection, timeSection, latestOrderSection, ownershipSection, imageSection, remainingSection]) {
    if (section) {
      sections.push(section);
    }
  }

  return sections.length ? sections : fallbackSection(payloadJson);
}
