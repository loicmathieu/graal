package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

public final class BytecodeOSRMetadata {
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, false, Integer.MAX_VALUE);
    public static final int OSR_POLL_INTERVAL = 1024; // must be a power of 2 (polling uses bit
                                                      // masks)

    private final BytecodeOSRNode osrNode;
    private final EconomicMap<Integer, OptimizedCallTarget> osrCompilations;
    private final int osrThreshold;
    // TODO: check whether these fields need to be volatile.
    private volatile boolean osrEnabled;
    private int backEdgeCount;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, boolean osrEnabled, int osrThreshold) {
        this.osrNode = osrNode;
        this.osrCompilations = EconomicMap.create();
        this.osrEnabled = osrEnabled;
        this.osrThreshold = osrThreshold;
        this.backEdgeCount = 0;
    }

    final Object onOSRBackEdge(VirtualFrame parentFrame, int target, TruffleLanguage<?> language) {
        if (!incrementAndPoll()) {
            return null;
        }
        OptimizedCallTarget osrTarget = osrCompilations.get(target);
        if (osrTarget == null) {
            synchronized (this) {
                osrTarget = osrCompilations.get(target);
                if (osrTarget == null) {
                    osrTarget = requestOSR(osrNode, target, language, parentFrame.getFrameDescriptor());
                    osrCompilations.put(target, osrTarget);
                }
            }
        }
        if (osrTarget != null && !osrTarget.isCompiling()) {
            if (osrTarget.isValid()) {
                // TODO: What if the frame descriptor changed since we created this call target?
                if (!GraalRuntimeAccessor.FRAME.getMaterializeCalled(parentFrame.getFrameDescriptor())) {
                    return osrTarget.call(parentFrame);
                }
                // We cannot perform OSR if the frame is materialized. The original and OSR
                // frames could get out of sync, which could lead to inconsistent views of the
                // program state.
                return null;
            }
            invalidateOSRTarget(target, "OSR compilation failed or cancelled");
        }
        return null;
    }

    private boolean incrementAndPoll() {
        /*
         * Increment back edge count and return whether compilation should be polled.
         * 
         * When OSR compilation is enabled and the OSR threshold is reached, this method will return
         * true after every OSR_POLL_INTERVAL back-edges. This method is thread-safe, but could
         * under-count.
         */
        int newBackEdgeCount = ++backEdgeCount; // Omit overflow check; OSR should trigger long
                                                // before overflow happens
        return (newBackEdgeCount >= osrThreshold && (newBackEdgeCount & (OSR_POLL_INTERVAL - 1)) == 0) &&
                        osrEnabled; // Only incur volatile read if poll condition met

    }

    private synchronized OptimizedCallTarget requestOSR(BytecodeOSRNode osrNode, int target, TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        assert !osrCompilations.containsKey(target);
        OptimizedCallTarget callTarget = GraalTruffleRuntime.getRuntime().createOSRCallTarget(new BytecodeOSRRootNode(osrNode, target, language, frameDescriptor));
        callTarget.compile(false);
        if (callTarget.isCompilationFailed()) {
            osrEnabled = false;
            return null;
        }
        osrCompilations.put(target, callTarget);
        return callTarget;
    }

    private synchronized void invalidateOSRTarget(int target, CharSequence reason) {
        OptimizedCallTarget callTarget = osrCompilations.removeKey(target);
        if (callTarget != null) {
            if (callTarget.isCompilationFailed()) {
                osrEnabled = false;
            }
            callTarget.invalidate(reason);
        }
    }

    synchronized void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        MapCursor<Integer, OptimizedCallTarget> cursor = osrCompilations.getEntries();
        while (cursor.advance()) {
            OptimizedCallTarget callTarget = cursor.getValue();
            if (callTarget != null) {
                if (callTarget.isCompilationFailed()) {
                    osrEnabled = false;
                }
                callTarget.nodeReplaced(oldNode, newNode, reason);
            }
        }
        osrCompilations.clear();
    }

    // for testing
    public EconomicMap<Integer, OptimizedCallTarget> getOSRCompilations() {
        return osrCompilations;
    }

    public int getBackEdgeCount() {
        return backEdgeCount;
    }
}
