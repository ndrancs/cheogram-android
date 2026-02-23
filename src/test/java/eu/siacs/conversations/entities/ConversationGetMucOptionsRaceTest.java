package eu.siacs.conversations.entities;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xmpp.Jid;
import junit.framework.Assert;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ConversationGetMucOptionsRaceTest {

    static int fieldReadCount;
    static volatile CountDownLatch remainingReads;
    static volatile CountDownLatch resetDone;

    public static void gate() {
        final var reads = remainingReads;
        final var reset = resetDone;
        if (reads == null || reset == null) return;
        final boolean lastRead = reads.getCount() == 1;
        reads.countDown();
        if (lastRead) {
            try {
                reset.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class GetMucOptionsInstrumentor extends MethodVisitor {
        private int count = 0;

        GetMucOptionsInstrumentor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitFieldInsn(
            int opcode, String owner, String name, String descriptor
        ) {
            if (opcode == Opcodes.GETFIELD && "mucOptions".equals(name)) {
                count++;
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(
                        ConversationGetMucOptionsRaceTest.class),
                    "gate",
                    "()V",
                    false
                );
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitEnd() {
            fieldReadCount = count;
            super.visitEnd();
        }
    }

    @SuppressWarnings("unchecked")
    @BeforeClass
    public static void instrumentConversation() throws Exception {
        Class.forName("net.bytebuddy.agent.ByteBuddyAgent")
            .getMethod("install")
            .invoke(null);

        final var strategy = (ClassLoadingStrategy<ClassLoader>)
            Class.forName(
                "net.bytebuddy.dynamic.loading.ClassReloadingStrategy")
                .getMethod("fromInstalledAgent")
                .invoke(null);

        new ByteBuddy()
            .redefine(Conversation.class)
            .visit(new AsmVisitorWrapper.ForDeclaredMethods()
                .method(
                    named("getMucOptions"),
                    new AsmVisitorWrapper.ForDeclaredMethods
                            .MethodVisitorWrapper() {
                        @Override
                        public MethodVisitor wrap(
                            TypeDescription instrumentedType,
                            MethodDescription instrumentedMethod,
                            MethodVisitor methodVisitor,
                            Implementation.Context implementationContext,
                            TypePool typePool,
                            int writerFlags,
                            int readerFlags
                        ) {
                            return new GetMucOptionsInstrumentor(
                                methodVisitor);
                        }
                    }
                )
            )
            .make()
            .load(Conversation.class.getClassLoader(), strategy);
    }

    @Test
    public void testGetMucOptionsNeverReturnsNull() throws Throwable {
        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(
            Jid.ofLocalAndDomain("testAccount", "example.org"));

        final var conversation = new Conversation(
            "Test MUC",
            account,
            Jid.ofLocalAndDomain("testMuc", "example.org"),
            Conversation.MODE_MULTI
        );
        conversation.getMucOptions();

        remainingReads = new CountDownLatch(fieldReadCount);
        resetDone = new CountDownLatch(1);

        final var result = new AtomicReference<MucOptions>();
        final var error = new AtomicReference<Throwable>();

        Thread reader = new Thread(() -> {
            try {
                result.set(conversation.getMucOptions());
            } catch (Throwable t) {
                error.set(t);
            }
        });

        Thread resetter = new Thread(() -> {
            try {
                remainingReads.await();
                conversation.resetMucOptions();
                resetDone.countDown();
            } catch (Throwable t) {
                error.set(t);
            }
        });

        reader.start();
        resetter.start();

        reader.join(10_000);
        resetter.join(10_000);

        remainingReads = null;
        resetDone = null;

        if (error.get() != null) throw error.get();

        Assert.assertNotNull(
            "getMucOptions() returned null"
                + " — the field must not be re-read after the null check",
            result.get()
        );
    }
}
