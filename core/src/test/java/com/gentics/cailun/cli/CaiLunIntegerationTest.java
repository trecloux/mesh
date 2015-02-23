package com.gentics.cailun.cli;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.gentics.cailun.etc.config.CaiLunConfiguration;
import com.gentics.cailun.test.AbstractIntegrationTest;

public class CaiLunIntegerationTest extends AbstractIntegrationTest {

	@Test
	public void testStartup() throws Exception {
		CaiLunConfiguration config = new CaiLunConfiguration();
		final CaiLun cailun = CaiLun.getInstance();
		final AtomicBoolean customLoaderInvoked = new AtomicBoolean(false);
		final AtomicBoolean caiLunStarted = new AtomicBoolean(false);
		cailun.setCustomLoader((vertx) -> {
			// deployAndWait(vertx, CustomerVerticle.class);
			customLoaderInvoked.set(true);
		});
		final CountDownLatch latch = new CountDownLatch(1);

		new Thread(() -> {
			try {
				cailun.run(config, () -> {
					assertTrue("The custom loader was not invoked during the startup process", customLoaderInvoked.get());
					caiLunStarted.set(true);
					latch.countDown();
				});
			} catch (Exception e) {
				fail("Error while starting instance: " + e.getMessage());
				e.printStackTrace();
			}
		}).start();
		if (latch.await(DEFAULT_TIMEOUT_MILISECONDS, TimeUnit.MILLISECONDS)) {
			assertTrue(caiLunStarted.get());
		} else {
			fail("Cailun did not startup on time. Timeout {" + DEFAULT_TIMEOUT_MILISECONDS + "} miliseconds reached.");
		}
	}
}
