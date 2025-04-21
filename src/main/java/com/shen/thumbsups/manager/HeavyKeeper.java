package com.shen.thumbsups.manager;

import java.util.*;
import java.util.concurrent.*;

import cn.hutool.core.util.HashUtil;
import lombok.Data;

/**
 * 基于HeavyKeeper算法的TopK计数器实现
 * @author shenguang
 */
public class HeavyKeeper implements TopK {
    // 常量定义 ====================================================
    private static final int LOOKUP_TABLE_SIZE = 256; // 衰减概率查找表最大索引值
    // 核心参数 ====================================================
    private final int k; // TopK阈值，维护的热门元素数量上限
    private final int width; // 哈希表的横向维度（每个哈希层的桶数量）
    private final int depth; // 哈希表的纵向维度（哈希层数）
    private final double[] lookupTable; // 预计算的指数衰减概率表
    private final int minCount; // 元素进入TopK的最小计数阈值
    // 数据结构 ====================================================
    private final Bucket[][] buckets; // 二维哈希桶阵列[depth][width]
    private final PriorityQueue<Node> minHeap; // 维护TopK的最小堆
    private final BlockingQueue<Item> expelledQueue; // 被淘汰元素的阻塞队列
    private final Random random; // 随机数生成器（用于衰减判定）
    private long total; // 总计数（所有元素的累计值）

    /**
     * 构造函数初始化核心数据结构
     *
     * @param k         TopK阈值
     * @param width     每个哈希层的桶数量
     * @param depth     哈希层数（纵向维度）
     * @param decay     指数衰减系数（0-1之间）
     * @param minCount  元素进入TopK的最小计数要求
     */
    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;

        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }

        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }

        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count)); // 构造最小堆
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();
        this.total = 0;
    }

    /**
     * 添加元素并更新TopK状态（基于多层衰减计数器和最小堆实现）
     *
     * 实现说明：
     * 1. 使用多层哈希结构进行频率统计和衰减淘汰
     * 2. 采用指数衰减策略维护近似计数
     * 3. 最小堆维护当前TopK热键
     *
     * @param key       元素唯一标识符（非空）
     * @param increment 要增加的计数值（必须>0，表示该元素出现的增量次数）
     * @return AddResult 包含三个信息：
     *         - expelled: 被淘汰出TopK的元素（当堆满且新元素超过堆顶时产生）
     *         - isHot: 是否成为/保持热键状态
     *         - currentKey: 当前操作键的状态信息（可用于调试）
     *
     * 核心流程：
     * 1. 多层哈希桶更新：遍历多层哈希结构，执行计数更新或衰减淘汰
     * 2. 全局计数维护：累加总操作次数用于后续统计
     * 3. TopK堆更新：通过最小堆维护当前最高频的K个元素
     */
    @Override
    public AddResult add(String key, int increment) {
        // 哈希预处理：生成64位指纹和初始最大计数
        byte[] keyBytes = key.getBytes();
        long itemFingerprint = hash(keyBytes);
        int maxCount = 0;

        // 多层哈希结构处理（类似布谷鸟过滤器机制）
        for (int i = 0; i < depth; i++) {
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
            Bucket bucket = buckets[i][bucketNumber];

            // 桶级同步保证线程安全
            synchronized (bucket) {
                /* 桶状态处理三态机：
                 * 1. 空桶：直接占据桶
                 * 2. 指纹匹配：累加计数
                 * 3. 指纹冲突：执行衰减淘汰流程
                 */
                if (bucket.count == 0) {
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    // 指数衰减淘汰策略：根据预计算的衰减概率表进行多次衰减尝试
                    for (int j = 0; j < increment; j++) {
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        if (random.nextDouble() < decay) {
                            bucket.count--;
                            if (bucket.count == 0) {
                                // 桶空出后占据该桶，并携带剩余增量
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 全局操作计数器更新（用于计算TPS等监控指标）
        total += increment;

        // 快速失败：未达到当前TopK最低阈值直接返回
        if (maxCount < minCount) {
            return new AddResult(null, false, null);
        }

        // 最小堆同步更新（保证TopK结构的线程安全）
        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;

            // 存在性检查：O(n)查找（适合小k值场景）
            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                // 更新现有元素：删除后重新插入以触发堆化
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
            } else {
                // 新元素插入逻辑：根据堆容量和阈值判断
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    Node newNode = new Node(key, maxCount);
                    if (minHeap.size() >= k) {
                        // 堆满时淘汰堆顶元素（当前最小元素）
                        expelled = minHeap.poll().key;
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    minHeap.add(newNode);
                    isHot = true;
                }
            }

            return new AddResult(expelled, isHot, key);
        }
    }


    /**
     * 获取当前TopK列表（按计数降序排列）
     */
    @Override
    public List<Item> list() {
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    /**
     * 获取被淘汰元素的队列（线程安全）
     */
    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }

    /**
     * 执行计数衰减操作（所有计数器值减半）
     * 用于实现时间衰减效果，降低旧数据的权重
     */
    @Override
    public void fading() {
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                }
            }
        }

        synchronized (minHeap) {
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            minHeap.clear();
            minHeap.addAll(newHeap);
        }

        total = total >> 1;
    }

    /**
     * 获取总计数（所有元素的累计值）
     */
    @Override
    public long total() {
        return total;
    }

    // 内部数据结构 ================================================
    /**
     * 哈希桶结构
     * 每个桶包含：
     * fingerprint - 指纹标识（用于冲突检测）
     * count       - 当前计数
     */
    private static class Bucket {
        long fingerprint;
        int count;
    }

    /**
     * TopK元素节点
     * key   - 元素标识
     * count - 当前计数
     */
    private static class Node {
        final String key;
        final int count;

        Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    // 哈希工具方法 ================================================
    /**
     * 使用MurmurHash3算法生成32位哈希值
     * @param data 输入字节数组
     * @return 32位哈希值
     */
    private static int hash(byte[] data) {
        return HashUtil.murmur32(data);
    }

}

// 新增返回结果类
@Data
class AddResult {
    // 被挤出的 key
    private final String expelledKey;
    // 当前 key 是否进入 TopK
    private final boolean isHotKey;
    // 当前操作的 key
    private final String currentKey;

    /**
     * @param expelledKey 被挤出TopK的元素（可能为null）
     * @param isHotKey    是否成为热key
     * @param currentKey  当前操作的key（用于跟踪）
     */
    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }

}
