package eu.siacs.conversations.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.IBinder;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.RosterManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ServiceTestRule;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
This test is extremely unreliable. I tried a lot of things to make it more reliable,
but ultimately there's not much to be done. The bug it captures is triggered about 1 in every 10
times. That bug is triggered by unsynchronized access to
`QuickConversationsService.mLastSyncAttempt`, which causes a whole host of problems, mostly
as a result of reading during resize, which can surface as:
- NPE from reading a partially constructed Node then treating the return value as non-null
- NoSuchMethodException, since apparently HashMap entries might optimize to a BST while another
thread expects a linked list node
- infinite loops caused by traversing a corrupted linked list
*/
@FlakyTest
@RunWith(AndroidJUnit4.class)
public class QuickConversationsServiceRaceTest {
	@Rule
	public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private List<Account> createTestAccounts(int count) {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Jid jid = Jid.of("testuser" + i + "@test.example.com");
            Account account = new Account(jid, "testpassword" + i);

            RosterManager mockRoster = mock(RosterManager.class);
            when(mockRoster.getWithSystemAccounts(any())).thenReturn(ImmutableList.of());

            XmppConnection mockConnection = mock(XmppConnection.class);
            when(mockConnection.getManager(RosterManager.class)).thenReturn(mockRoster);

            account.setXmppConnection(mockConnection);

            accounts.add(account);
        }
        return accounts;
    }

    private Method getConsiderSyncMethod(QuickConversationsService qcs) throws NoSuchMethodException {
        Method method = qcs.getClass().getDeclaredMethod(
            "considerSync", Account.class, Set.class, Map.class, boolean.class
        );
        method.setAccessible(true);
        return method;
    }


    private Map<String, ?> getLastSyncAttemptMap(QuickConversationsService qcs) throws Exception {
        Field field = qcs.getClass().getDeclaredField("mLastSyncAttempt");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(qcs);
    }

    private void setAccounts(XmppConnectionService xcs, List<Account> accounts) throws Exception {
        Field field = xcs.getClass().getDeclaredField("accounts");
        field.setAccessible(true);
        field.set(xcs, accounts);
    }

    private void injectAlwaysResizingHashMap(QuickConversationsService qcs) throws Exception {
        Field field = qcs.getClass().getDeclaredField("mLastSyncAttempt");
        field.setAccessible(true);
        field.set(qcs, new AlwaysResizingHashMap<>());
    }

    static class AlwaysResizingHashMap<K, V> extends HashMap<K, V> {
        private final AtomicInteger churnCounter = new AtomicInteger(0);
        private final AtomicInteger opCounter = new AtomicInteger(0);

        public AlwaysResizingHashMap() {
            super(1);
        }

        @SuppressWarnings("unchecked")
        private void churn() {
            int base = churnCounter.getAndAdd(10);
            for (int i = 0; i < 10; i++) {
                super.put((K) ("__churn_" + (base + i)), null);
            }
            for (int i = 0; i < 10; i++) {
                super.remove("__churn_" + (base + i));
            }
            if (opCounter.incrementAndGet() % 3 == 0) {
                super.clear();
            }
        }

        @Override
        public V put(K key, V value) {
            V result = super.put(key, value);
            churn();
            return result;
        }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            churn();
            return super.getOrDefault(key, defaultValue);
        }
    }

    @Test
    public void resizeRace() throws Throwable {
		IBinder binder = mServiceRule.bindService(
				new Intent(InstrumentationRegistry.getTargetContext(), XmppConnectionService.class));
		XmppConnectionService xmppConnectionService =
				((XmppConnectionService.XmppConnectionBinder) binder).getService();
		QuickConversationsService service = xmppConnectionService.getQuickConversationsService();

		final var accounts = createTestAccounts(5);
		setAccounts(xmppConnectionService, accounts);
		// Uncomment to make the race condition slightly more likely with a non-thread-safe HashMap.
		// When commented out, the test verifies that the ConcurrentHashMap fix prevents races.
		// injectAlwaysResizingHashMap(service);

		final AtomicReference<Throwable> caughtException = new AtomicReference<>();
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch doneLatch = new CountDownLatch(4);

		Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> {
			caughtException.compareAndSet(null, e);
		};

		final Method considerSyncForced = service.getClass().getDeclaredMethod("considerSync", boolean.class);
		considerSyncForced.setAccessible(true);

		Thread thread1 = new Thread(() -> {
			try {
				startLatch.await();
				for (int i = 0; i < 500; ++i) {
					considerSyncForced.invoke(service, true);
				}
			} catch (Throwable e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				caughtException.compareAndSet(null, cause);
			} finally {
				doneLatch.countDown();
			}
		});
		thread1.setUncaughtExceptionHandler(exceptionHandler);

		Thread thread2 = new Thread(() -> {
			try {
				startLatch.await();
				for (int i = 0; i < 500; ++i) {
					considerSyncForced.invoke(service, true);
				}
			} catch (Throwable e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				caughtException.compareAndSet(null, cause);
			} finally {
				doneLatch.countDown();
			}
		});
		thread2.setUncaughtExceptionHandler(exceptionHandler);

		Thread thread3 = new Thread(() -> {
			try {
				startLatch.await();
				for (int i = 0; i < 500; ++i) {
					considerSyncForced.invoke(service, true);
				}
			} catch (Throwable e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				caughtException.compareAndSet(null, cause);
			} finally {
				doneLatch.countDown();
			}
		});
		thread3.setUncaughtExceptionHandler(exceptionHandler);

		Thread thread4 = new Thread(() -> {
			try {
				startLatch.await();
				for (int i = 0; i < 500; ++i) {
					considerSyncForced.invoke(service, true);
				}
			} catch (Throwable e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				caughtException.compareAndSet(null, cause);
			} finally {
				doneLatch.countDown();
			}
		});
		thread4.setUncaughtExceptionHandler(exceptionHandler);

		thread1.start();
		thread2.start();
		thread3.start();
		thread4.start();
		startLatch.countDown();

		boolean completed = doneLatch.await(5, TimeUnit.MINUTES);
		if (!completed) {
			String stuckInfo = getThreadStacks(thread1, thread2, thread3, thread4);
			thread1.interrupt();
			thread2.interrupt();
			thread3.interrupt();
			thread4.interrupt();
			throw new AssertionError("Timeout after 5 minutes.\n" + stuckInfo);
		}

		Throwable e = caughtException.get();
		if (e != null && isRaceException(e)) {
			throw e;
		}
    }

    private String getThreadStacks(Thread... threads) {
        StringBuilder result = new StringBuilder();
        for (Thread thread : threads) {
            StackTraceElement[] stack = thread.getStackTrace();
            result.append("Thread ").append(thread.getName())
                  .append(" (").append(thread.getState()).append("):\n");
            for (StackTraceElement ste : stack) {
                result.append("    at ").append(ste).append("\n");
            }
            result.append("\n");
        }
        return result.toString();
    }


//    private String findThreadsStuckInHashMap() {
//        StringBuilder result = new StringBuilder();
//        Map<Thread, StackTraceElement[]> allStacks = Thread.getAllStackTraces();
//
//        for (Map.Entry<Thread, StackTraceElement[]> entry : allStacks.entrySet()) {
//            Thread thread = entry.getKey();
//            String name = thread.getName();
//
//            if (!name.startsWith("Writer-") && !name.startsWith("Reader-")) {
//                continue;
//            }
//
//            StackTraceElement[] stack = entry.getValue();
//
//
//            if (inHashMap && fromConsiderSync) {
//                result.append("\nThread ").append(name).append(" stack trace:\n");
//                for (StackTraceElement ste : stack) {
//                    result.append("    at ").append(ste).append("\n");
//                }
//                Log.e(TAG, "Thread " + name + " stuck in HashMap (from considerSync):");
//                for (StackTraceElement ste : stack) {
//                    Log.e(TAG, "    at " + ste);
//                }
//            }
//        }
//
//        return result.length() > 0 ? result.toString() : null;
//    }

    /**
     * Check if the exception indicates a HashMap race condition.
     * OOM from HashMap.resize called from QuickConversationsService.considerSync
     * indicates race-corrupted state causing massive allocation.
     */
    private boolean isRaceException(Throwable t) {
        if (t instanceof NullPointerException ||
            t instanceof ClassCastException ||
            t instanceof ConcurrentModificationException) {
            return true;
        }
        // OOM specifically from HashMap.resize in our considerSync method
        if (t instanceof OutOfMemoryError) {
            boolean hasHashMapResize = false;
            boolean hasConsiderSync = false;
            for (StackTraceElement ste : t.getStackTrace()) {
                if ("java.util.HashMap".equals(ste.getClassName()) &&
                    "resize".equals(ste.getMethodName())) {
                    hasHashMapResize = true;
                }
                if ("eu.siacs.conversations.services.QuickConversationsService".equals(ste.getClassName()) &&
                    "considerSync".equals(ste.getMethodName())) {
                    hasConsiderSync = true;
                }
            }
            return hasHashMapResize && hasConsiderSync;
        }
        return false;
    }
}
