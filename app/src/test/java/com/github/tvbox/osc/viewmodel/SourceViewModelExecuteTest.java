package com.github.tvbox.osc.viewmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * P-3 回归：searchExecutorService 的有界队列拒绝契约。
 *
 * 历史坑：P-3 实现用了 DiscardPolicy——拒绝被静默吞掉，execute() 捕获的
 * RejectedExecutionException 永远不会抛出，恒返回 true，「队列满回滚计数」
 * 从未生效（搜索计数泄漏的另一半根因）。本测试锁定 Abort 语义：
 * 5 个工作线程占满 + 64 队列占满后，第 70 个 execute 必须返回 false。
 */
public class SourceViewModelExecuteTest {

    private static final int WORKERS = 5;
    private static final int QUEUE = 64;
    private static final int TOTAL_CAPACITY = WORKERS + QUEUE;

    private SourceViewModel viewModel;
    private CountDownLatch release;

    @Before
    public void setUp() {
        viewModel = new SourceViewModel();
        release = new CountDownLatch(1);
    }

    @After
    public void tearDown() {
        release.countDown();
        if (viewModel != null) {
            viewModel.shutdownNow();
            viewModel.destroyExecutor();
        }
    }

    private Runnable blockingTask() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    @Test
    public void executeReturnsFalseWhenQueueFull() {
        viewModel.initExecutor();
        for (int i = 0; i < TOTAL_CAPACITY; i++) {
            assertTrue("容量内任务 #" + i + " 应被接受", viewModel.execute(blockingTask()));
        }
        // 第 5+64+1 个：线程满、队列满 → 拒绝，调用方需回滚计数
        assertFalse("超出容量的任务必须被拒绝（返回 false）", viewModel.execute(blockingTask()));
        // 释放后可以继续提交
        release.countDown();
    }

    @Test
    public void executeReturnsFalseWhenExecutorDestroyed() {
        viewModel.destroyExecutor();
        assertFalse(viewModel.execute(new Runnable() {
            @Override
            public void run() {
            }
        }));
    }

    @Test
    public void initExecutorReplacesPreviousPool() {
        viewModel.initExecutor();
        viewModel.initExecutor();
        // 重建后新池空载，容量内任务可接受
        assertTrue(viewModel.execute(new Runnable() {
            @Override
            public void run() {
            }
        }));
    }
}
