/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.builtins.objects.common.HashingStorage.DEFAULT_EQIVALENCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.FastDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonNativeObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectHybridDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.Equivalence;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ContainsKeyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.ContainsValueNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.CopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DelItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DiffNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.DynamicObjectSetItemNodeFactory.DynamicObjectSetItemCachedNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.EqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.InvalidateMroNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysEqualsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.KeysIsSubsetNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.UnionNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.LazyString;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.PassCaughtExceptionNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@GenerateNodeFactory
public abstract class HashingStorageNodes {
    private static final int MAX_STORAGES = 8;

    public static class PythonEquivalence extends Equivalence {
        @Child private PythonObjectLibrary library = PythonObjectLibrary.getFactory().createDispatched(3);
        @Child private BinaryComparisonNode callEqNode = BinaryComparisonNode.create(__EQ__, __EQ__, "==", null, null);
        @Child private CastToBooleanNode castToBoolean = CastToBooleanNode.createIfTrueNode();

        @Override
        public int hashCode(Object o) {
            long hash = library.hash(o);
            // Use a sufficiently bit-mixing narrowing conversion...
            return Long.hashCode(hash);
        }

        @Override
        public boolean equals(Object left, Object right) {
            return castToBoolean.executeBoolean(null, callEqNode.executeWith(null, left, right));
        }

        public static PythonEquivalence create() {
            return new PythonEquivalence();
        }
    }

    // TODO qualified name is a workaround for a DSL bug
    @com.oracle.truffle.api.dsl.ImportStatic(PGuards.class)
    abstract static class DictStorageBaseNode extends com.oracle.truffle.api.nodes.Node implements com.oracle.graal.python.nodes.IndirectCallNode {
        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Child private PRaiseNode raise;
        @Child private GetLazyClassNode getClassNode;
        @Child private Equivalence equivalenceNode;
        @Child private PassCaughtExceptionNode passExceptionNode;

        @CompilationFinal private ContextReference<PythonContext> contextRef;
        @CompilationFinal private IsBuiltinClassProfile isBuiltinClassProfile;

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        protected Equivalence getEquivalence() {
            if (equivalenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equivalenceNode = insert(new PythonEquivalence());
            }
            return equivalenceNode;
        }

        protected LazyPythonClass getClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(object);
        }

        protected boolean isJavaString(Object o) {
            return o instanceof String || o instanceof PString && wrappedString((PString) o);
        }

        protected static boolean shapeCheck(Shape shape, DynamicObject receiver) {
            return shape != null && shape.check(receiver);
        }

        protected static Shape lookupShape(DynamicObject receiver) {
            CompilerAsserts.neverPartOfCompilation();
            return receiver.getShape();
        }

        protected PRaiseNode getRaise() {
            if (raise == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raise = insert(PRaiseNode.create());
            }
            return raise;
        }

        protected PException unhashable(Object key) {
            return getRaise().raise(TypeError, "unhashable type: '%p'", key);
        }

        protected boolean wrappedString(PString s) {
            if (isBuiltinClassProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinClassProfile = IsBuiltinClassProfile.create();
            }
            return isBuiltinClassProfile.profileClass(getClass(s), PythonBuiltinClassType.PString);
        }

        protected EconomicMapStorage switchToEconomicMap(HashingStorage storage) {
            // We cannot store this key in the dynamic object -> switch to generic store
            EconomicMapStorage newStorage = EconomicMapStorage.create(storage.length() + 1, false);
            newStorage.addAll(storage, getEquivalence());
            return newStorage;
        }

        protected final ContextReference<PythonContext> getContextRef() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef;
        }

        protected final PException passException(VirtualFrame frame) {
            if (passExceptionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                passExceptionNode = insert(PassCaughtExceptionNode.create());
            }
            return passExceptionNode.execute(frame);
        }

        protected static EconomicMapStorage switchToEconomicMap(HashingStorage storage, Equivalence equiv) {
            // We cannot store this key in the dynamic object -> switch to generic store
            EconomicMapStorage newStorage = EconomicMapStorage.create(storage.length() + 1, false);
            newStorage.addAll(storage, equiv);
            return newStorage;
        }

        protected static DynamicObjectStorage switchToFastDictStorage(HashingStorage storage) {
            DynamicObjectStorage newStorage = new FastDictStorage();
            newStorage.addAll(storage, DEFAULT_EQIVALENCE);
            return newStorage;
        }

        protected static PythonObjectHybridDictStorage switchToHybridDictStorage(PythonObjectDictStorage dictStorage) {
            return new PythonObjectHybridDictStorage(dictStorage);
        }

        protected static Location lookupLocation(Shape shape, Object name) {
            /* Initialization of cached values always happens in a slow path. */
            CompilerAsserts.neverPartOfCompilation();

            Property property = shape.getProperty(name);
            if (property != null) {
                return property.getLocation();
            }
            return null;
        }

        protected static boolean exceedsLimit(DynamicObjectStorage storage) {
            return storage instanceof FastDictStorage && ((FastDictStorage) storage).length() + 1 >= DynamicObjectStorage.SIZE_THRESHOLD;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class InitNode extends DictStorageBaseNode {

        public abstract HashingStorage execute(VirtualFrame frame, Object mapping, PKeyword[] kwargs);

        @Child private GetNextNode nextNode;
        @Child private SetItemNode setItemNode;
        @Child private LookupInheritedAttributeNode lookupKeysAttributeNode;

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        HashingStorage doEmpty(@SuppressWarnings("unused") PNone iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return new EmptyStorage();
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        HashingStorage doKeywords(@SuppressWarnings("unused") PNone iterable, PKeyword[] kwargs) {
            return new KeywordsStorage(kwargs);
        }

        protected static boolean isPDict(PythonObject o) {
            return o instanceof PHashingCollection;
        }

        protected boolean hasKeysAttribute(PythonObject o) {
            if (lookupKeysAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupKeysAttributeNode = insert(LookupInheritedAttributeNode.create(KEYS));
            }
            return lookupKeysAttributeNode.execute(o) != PNone.NO_VALUE;
        }

        @Specialization(guards = "isEmpty(kwargs)")
        HashingStorage doPDict(PHashingCollection dictLike, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached("create()") HashingCollectionNodes.GetDictStorageNode getDictStorageNode) {
            return getDictStorageNode.execute(dictLike).copy(HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "!isEmpty(kwargs)", rewriteOn = HashingStorage.UnmodifiableStorageException.class)
        HashingStorage doPDictKwargs(VirtualFrame frame, PHashingCollection dictLike, PKeyword[] kwargs,
                        @Cached("create()") UnionNode unionNode,
                        @Cached("create()") HashingCollectionNodes.GetDictStorageNode getDictStorageNode) {
            return unionNode.execute(frame, getDictStorageNode.execute(dictLike), new KeywordsStorage(kwargs));
        }

        @Specialization(guards = "!isEmpty(kwargs)")
        HashingStorage doPDictKwargs(VirtualFrame frame, PDict iterable, PKeyword[] kwargs,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            HashingStorage dictStorage = iterable.getDictStorage().copy(HashingStorage.DEFAULT_EQIVALENCE);
            unionNode.execute(frame, dictStorage, new KeywordsStorage(kwargs));
            return dictStorage;
        }

        @Specialization(guards = {"!isPDict(mapping)", "hasKeysAttribute(mapping)"})
        HashingStorage doMapping(VirtualFrame frame, PythonObject mapping, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {

            HashingStorage curStorage = PDict.createNewStorage(false, 0);

            // That call must work since 'hasKeysAttribute' checks if it has the 'keys' attribute
            // before.
            Object keysIterable = callKeysNode.executeObject(frame, mapping);

            Object keysIt = getIteratorNode.executeWith(frame, keysIterable);
            while (true) {
                try {
                    Object keyObj = getNextNode().execute(frame, keysIt);
                    Object valueObj = callGetItemNode.executeObject(frame, mapping, keyObj);

                    curStorage = getSetItemNode().execute(frame, curStorage, keyObj, valueObj);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return curStorage;
                }
            }
        }

        private GetNextNode getNextNode() {
            if (nextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextNode = insert(GetNextNode.create());
            }
            return nextNode;
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPDict(iterable)", "!hasKeysAttribute(iterable)"})
        HashingStorage doSequence(VirtualFrame frame, PythonObject iterable, PKeyword[] kwargs,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") FastConstructListNode createListNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached("create()") SequenceNodes.LenNode seqLenNode,
                        @Cached("createBinaryProfile()") ConditionProfile lengthTwoProfile,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") IsBuiltinClassProfile isTypeErrorProfile) {

            Object it = getIterator.executeWith(frame, iterable);

            ArrayList<PSequence> elements = new ArrayList<>();
            boolean isStringKey = false;
            try {
                while (true) {
                    Object next = getNextNode().execute(frame, it);
                    PSequence element = null;
                    int len = 1;
                    element = createListNode.execute(next);
                    assert element != null;
                    // This constructs a new list using the builtin type. So, the object cannot
                    // be subclassed and we can directly call 'len()'.
                    len = seqLenNode.execute(element);

                    if (lengthTwoProfile.profile(len != 2)) {
                        throw getRaise().raise(ValueError, "dictionary update sequence element #%d has length %d; 2 is required", arrayListSize(elements), len);
                    }

                    // really check for Java String since PString can be subclassed
                    isStringKey = isStringKey || getItemNode.executeObject(frame, element, 0) instanceof String;

                    arrayListAdd(elements, element);
                }
            } catch (PException e) {
                if (isTypeErrorProfile.profileException(e, TypeError)) {
                    throw getRaise().raise(TypeError, "cannot convert dictionary update sequence element #%d to a sequence", arrayListSize(elements));
                } else {
                    e.expectStopIteration(errorProfile);
                }
            }

            HashingStorage storage = PDict.createNewStorage(isStringKey, arrayListSize(elements) + kwargs.length);
            for (int j = 0; j < arrayListSize(elements); j++) {
                PSequence element = arrayListGet(elements, j);
                storage = getSetItemNode().execute(frame, storage, getItemNode.executeObject(frame, element, 0), getItemNode.executeObject(frame, element, 1));
            }
            if (kwargs.length > 0) {
                storage.addAll(new KeywordsStorage(kwargs));
            }
            return storage;
        }

        @TruffleBoundary(allowInlining = true)
        private static PSequence arrayListGet(ArrayList<PSequence> elements, int j) {
            return elements.get(j);
        }

        @TruffleBoundary(allowInlining = true)
        private static boolean arrayListAdd(ArrayList<PSequence> elements, PSequence element) {
            return elements.add(element);
        }

        @TruffleBoundary(allowInlining = true)
        private static int arrayListSize(ArrayList<PSequence> elements) {
            return elements.size();
        }

        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    public abstract static class ContainsKeyNode extends DictStorageBaseNode {

        public abstract boolean execute(VirtualFrame frame, HashingStorage storage, Object key);

        @Specialization
        @SuppressWarnings("unused")
        protected boolean contains(EmptyStorage storage, Object key) {
            return false;
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        protected boolean contains(KeywordsStorage storage, Object key,
                        @Cached("createClassProfile()") ValueProfile keyTypeProfile,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupHash,
                        @Cached LookupAttributeInMRONode.Dynamic lookupStringHash,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            Object profileKey = keyTypeProfile.profile(key);
            if (profileKey instanceof String) {
                return storage.hasKey(profileKey, DEFAULT_EQIVALENCE);
            } else if (profileKey instanceof PString) {
                if (wrappedString((PString) profileKey) || lookupHash.execute(key, __HASH__) == lookupStringHash.execute(PythonBuiltinClassType.PString, __HASH__)) {
                    return storage.hasKey(((PString) profileKey).getValue(), DEFAULT_EQIVALENCE);
                }
                CompilerDirectives.transferToInterpreter();
                // see GR-17389
                throw new RuntimeException("String subclasses with custom hash in dict not implemented.");
            }
            return false;
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "wrappedString(name)",
                                        "cachedName.equals(name.getValue())",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectPString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") PString name,
                        @SuppressWarnings("unused") @Cached("name.getValue()") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, cachedName)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectPString"}, guards = {"wrappedString(name)", "storage.getStore().getShape().isValid()"})
        protected boolean readUncachedPString(DynamicObjectStorage storage, PString name) {
            return storage.getStore().containsKey(name.getValue());
        }

        @Specialization(guards = {"wrappedString(name)", "!storage.getStore().getShape().isValid()"})
        protected boolean updateShapePString(DynamicObjectStorage storage, PString name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectString"}, guards = "storage.getStore().getShape().isValid()")
        protected boolean readUncached(DynamicObjectStorage storage, String name) {
            return storage.getStore().containsKey(name);
        }

        @Specialization(guards = "!storage.getStore().getShape().isValid()")
        protected boolean updateShape(DynamicObjectStorage storage, String name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(guards = "!isJavaString(name)")
        @SuppressWarnings("try")
        protected boolean readUncached(VirtualFrame frame, PythonObjectHybridDictStorage storage, Object name) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.hasKey(name, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "!isJavaString(name)")
        protected boolean readUncached(VirtualFrame frame, DynamicObjectStorage storage, PString name,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupHash,
                        @Cached LookupAttributeInMRONode.Dynamic lookupStringHash,
                        @Cached ContainsKeyNode recursiveNode) {
            if (lookupHash.execute(name, __HASH__) == lookupStringHash.execute(PythonBuiltinClassType.PString, __HASH__)) {
                return recursiveNode.execute(frame, storage, name.getValue());
            }
            CompilerDirectives.transferToInterpreter();
            // see GR-17389
            throw new RuntimeException("String subclasses with custom hash in dict not implemented.");
        }

        @Specialization(guards = {"!isJavaString(name)", "!isPString(name)"})
        @SuppressWarnings("unused")
        protected boolean readUncached(DynamicObjectStorage storage, Object name) {
            return false;
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected boolean contains(VirtualFrame frame, EconomicMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.hasKey(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected boolean contains(VirtualFrame frame, HashMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.hasKey(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        protected boolean contains(LocalsStorage storage, PString key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return storage.hasKey(key.getValue(), HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        protected boolean contains(LocalsStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return storage.hasKey(key, HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "1")
        protected boolean doUnhashable(@SuppressWarnings("unused") EconomicMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            throw unhashable(key);
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "1")
        protected boolean doUnhashable(@SuppressWarnings("unused") HashMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            throw unhashable(key);
        }

        public static ContainsKeyNode create() {
            return ContainsKeyNodeGen.create();
        }
    }

    public abstract static class ContainsValueNode extends DictStorageBaseNode {

        public abstract boolean execute(VirtualFrame frame, HashingStorage storage, Object value);

        @Specialization
        @SuppressWarnings("unused")
        protected boolean contains(EmptyStorage storage, Object value) {
            return false;
        }

        @Specialization
        protected boolean contains(KeywordsStorage storage, Object value,
                        @Cached("createClassProfile()") ValueProfile keyTypeProfile) {

            Object profileKey = keyTypeProfile.profile(value);
            PKeyword[] store = storage.getStore();
            for (int i = 0; i < store.length; i++) {
            }

            if (profileKey instanceof String) {
                return storage.hasKey(value, DEFAULT_EQIVALENCE);
            }
            return false;
        }

        @Specialization(limit = "3", //
                        guards = {
                                        "cachedName.equals(name)",
                                        "shapeCheck(shape, storage.getStore())"
                        }, //
                        assumptions = {
                                        "shape.getValidAssumption()"
                        })
        protected static boolean doDynamicObjectString(@SuppressWarnings("unused") DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                        @SuppressWarnings("unused") @Cached("name") String cachedName,
                        @SuppressWarnings("unused") @Cached("lookupShape(storage.getStore())") Shape shape,
                        @Cached("lookupLocation(shape, name)") Location location) {
            return location != null;
        }

        @TruffleBoundary
        @Specialization(replaces = {"doDynamicObjectString"}, guards = "storage.getStore().getShape().isValid()")
        protected boolean readUncached(DynamicObjectStorage storage, String name) {
            return storage.getStore().containsKey(name);
        }

        @Specialization(guards = "!storage.getStore().getShape().isValid()")
        protected boolean updateShape(DynamicObjectStorage storage, String name) {
            CompilerDirectives.transferToInterpreter();
            storage.getStore().updateShape();
            return readUncached(storage, name);
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected boolean contains(VirtualFrame frame, EconomicMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.hasKey(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected boolean contains(VirtualFrame frame, HashMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.hasKey(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        protected boolean contains(LocalsStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return storage.hasKey(key, HashingStorage.DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "1")
        protected boolean doUnhashable(@SuppressWarnings("unused") HashMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            throw unhashable(key);
        }

        public static ContainsValueNode create() {
            return ContainsValueNodeGen.create();
        }
    }

    abstract static class SetItemBaseNode extends DictStorageBaseNode {

        /**
         * Try to find the given property in the shape. Also returns null when the value cannot be
         * store into the location.
         */
        protected static Location lookupLocation(Shape shape, Object name, Object value) {
            Location location = DictStorageBaseNode.lookupLocation(shape, name);
            if (location == null || !location.canSet(value)) {
                /* Existing property has an incompatible type, so a shape change is necessary. */
                return null;
            }

            return location;
        }

        protected static Shape defineProperty(Shape oldShape, Object name, Object value) {
            return oldShape.defineProperty(name, value, 0);
        }

        protected static boolean canSet(Location location, Object value) {
            return location.canSet(value);
        }

        protected static boolean canStore(Location location, Object value) {
            return location.canStore(value);
        }

    }

    public abstract static class SetItemNode extends SetItemBaseNode {

        @Child private DynamicObjectSetItemNode dynamicObjectSetItemNode;
        @Child private CastPStringToJavaStringNode castPStringToStringNode;

        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage storage, Object key, Object value);

        @Specialization
        protected HashingStorage doEmptyStorage(VirtualFrame frame, EmptyStorage storage, String key, Object value) {
            // immediately replace storage since empty storage is immutable
            DynamicObjectSetItemNode ensureDynamicObjectSetItemNode = ensureDynamicObjectSetItemNode();
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return ensureDynamicObjectSetItemNode.execute(switchToFastDictStorage(storage), key, value);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "wrappedString(key)")
        protected HashingStorage doEmptyStorage(VirtualFrame frame, EmptyStorage storage, PString key, Object value) {
            // immediately replace storage since empty storage is immutable
            DynamicObjectSetItemNode ensureDynamicObjectSetItemNode = ensureDynamicObjectSetItemNode();
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return ensureDynamicObjectSetItemNode.execute(switchToFastDictStorage(storage), cast(key), value);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doEmptyStorage(VirtualFrame frame, @SuppressWarnings("unused") EmptyStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            // immediately replace storage since empty storage is immutable
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                EconomicMapStorage newStorage = EconomicMapStorage.create(false);
                newStorage.setItem(key, value, getEquivalence());
                return newStorage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization
        protected HashingStorage doDynamicObject(VirtualFrame frame, DynamicObjectStorage storage, String key, Object value) {
            DynamicObjectSetItemNode ensureDynamicObjectSetItemNode = ensureDynamicObjectSetItemNode();
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return ensureDynamicObjectSetItemNode.execute(storage, key, value);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "wrappedString(key)")
        protected HashingStorage doDynamicObjectPString(VirtualFrame frame, DynamicObjectStorage storage, PString key, Object value) {
            DynamicObjectSetItemNode ensureDynamicObjectSetItemNode = ensureDynamicObjectSetItemNode();
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return ensureDynamicObjectSetItemNode.execute(storage, cast(key), value);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization
        protected HashingStorage doLocalsStringGeneralize(LocalsStorage storage, String key, Object value) {
            HashingStorage newStorage = switchToFastDictStorage(storage);
            newStorage.setItem(key, value, DEFAULT_EQIVALENCE);
            return newStorage;
        }

        @Specialization(guards = "wrappedString(key)")
        protected HashingStorage doLocalsPStringGeneralize(LocalsStorage storage, PString key, Object value) {
            HashingStorage newStorage = switchToFastDictStorage(storage);
            newStorage.setItem(cast(key), value, DEFAULT_EQIVALENCE);
            return newStorage;
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doLocalsGeneralize(VirtualFrame frame, LocalsStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                HashingStorage newStorage = switchToEconomicMap(storage);
                newStorage.setItem(key, value, getEquivalence());
                return newStorage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization
        protected HashingStorage doKeywordsStringGeneralize(KeywordsStorage storage, String key, Object value) {
            HashingStorage newStorage = switchToFastDictStorage(storage);
            newStorage.setItem(key, value, DEFAULT_EQIVALENCE);
            return newStorage;
        }

        @Specialization(guards = "wrappedString(key)")
        protected HashingStorage doKeywordsPStringGeneralize(KeywordsStorage storage, PString key, Object value) {
            HashingStorage newStorage = switchToFastDictStorage(storage);
            newStorage.setItem(key, value, DEFAULT_EQIVALENCE);
            return newStorage;
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doDynamicObjectGeneralize(VirtualFrame frame, PythonObjectDictStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            HashingStorage newStorage = switchToHybridDictStorage(storage);
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                newStorage.setItem(key, value, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
            return newStorage;
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doDynamicObjectGeneralize(VirtualFrame frame, FastDictStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                HashingStorage newStorage = switchToEconomicMap(storage);
                newStorage.setItem(key, value, getEquivalence());
                return newStorage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doKeywordsGeneralize(VirtualFrame frame, KeywordsStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            // immediately replace storage since keywords storage is immutable
            EconomicMapStorage newStorage = EconomicMapStorage.create(storage.length() + 1, false);
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                newStorage.addAll(storage, getEquivalence());
                newStorage.setItem(key, value, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
            return newStorage;
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doHashMap(VirtualFrame frame, EconomicMapStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                storage.setItem(key, value, getEquivalence());
                return storage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("try")
        protected HashingStorage doHashMap(VirtualFrame frame, HashMapStorage storage, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                storage.setItem(key, value, getEquivalence());
                return storage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "1")
        @SuppressWarnings("unused")
        protected HashingStorage doUnhashable(HashingStorage storage, Object key, Object value,
                        @CachedLibrary("key") PythonObjectLibrary lib) {
            throw unhashable(key);
        }

        private String cast(PString obj) {
            if (castPStringToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castPStringToStringNode = insert(CastPStringToJavaStringNode.create());
            }
            return castPStringToStringNode.execute(obj);
        }

        private DynamicObjectSetItemNode ensureDynamicObjectSetItemNode() {
            if (dynamicObjectSetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dynamicObjectSetItemNode = insert(DynamicObjectSetItemNode.create());
            }
            return dynamicObjectSetItemNode;
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    @GenerateUncached
    protected abstract static class InvalidateMroNode extends Node {
        public abstract void execute(DynamicObjectStorage s, String key, Object val);

        @Specialization
        static void doPythonNativeObjectDictStorage(PythonNativeObjectDictStorage storage, String key, @SuppressWarnings("unused") Object val) {
            storage.invalidateAttributeInMROFinalAssumptions(key);
        }

        @Specialization(guards = "!isNativeObjectDictStorage(storage)")
        @SuppressWarnings("unused")
        static void doPythonNativeObjectDictStorage(DynamicObjectStorage storage, String key, Object val) {
            // do nothing
        }

        protected static boolean isNativeObjectDictStorage(DynamicObjectStorage storage) {
            return storage instanceof PythonNativeObjectDictStorage;
        }

        public static InvalidateMroNode create() {
            return InvalidateMroNodeGen.create();
        }

        public static InvalidateMroNode getUncached() {
            return InvalidateMroNodeGen.getUncached();
        }
    }

    public abstract static class DynamicObjectSetItemNode extends SetItemBaseNode {

        public abstract HashingStorage execute(DynamicObjectStorage s, Object key, Object val);

        @TypeSystemReference(PythonArithmeticTypes.class)
        abstract static class DynamicObjectSetItemCachedNode extends DynamicObjectSetItemNode {
            @Child private InvalidateMroNode actionNode = InvalidateMroNode.create();

            // write to existing property; no shape change
            @Specialization(limit = "3", //
                            guards = {
                                            "cachedName.equals(name)",
                                            "shapeCheck(shape, storage.getStore())",
                                            "location != null",
                                            "canSet(location, value)"
                            }, //
                            assumptions = {
                                            "shape.getValidAssumption()"
                            })
            protected HashingStorage doDynamicObjectExistingCached(DynamicObjectStorage storage, @SuppressWarnings("unused") String name,
                            Object value,
                            @SuppressWarnings("unused") @Cached("name") String cachedName,
                            @Cached("lookupShape(storage.getStore())") Shape shape,
                            @Cached("lookupLocation(shape, name, value)") Location location) {
                try {
                    location.set(storage.getStore(), value, shape);
                    actionNode.execute(storage, name, value);
                    return storage;
                } catch (IncompatibleLocationException | FinalLocationException ex) {
                    /* Our guards ensure that the value can be stored, so this cannot happen. */
                    throw new IllegalStateException(ex);
                }
            }

            // add new property; shape change
            @Specialization(limit = "3", //
                            guards = {
                                            "cachedName.equals(name)",
                                            "shapeCheck(oldShape, storage.getStore())",
                                            "oldLocation == null",
                                            "canStore(newLocation, value)"
                            }, //
                            assumptions = {
                                            "oldShape.getValidAssumption()",
                                            "newShape.getValidAssumption()"
                            })
            @SuppressWarnings("unused")
            protected HashingStorage doDynamicObjectNewCached(DynamicObjectStorage storage, String name, Object value,
                            @Cached("name") String cachedName,
                            @Cached("lookupShape(storage.getStore())") Shape oldShape,
                            @Cached("lookupLocation(oldShape, name, value)") Location oldLocation,
                            @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                            @Cached("lookupLocation(newShape, name)") Location newLocation) {
                try {
                    newLocation.set(storage.getStore(), value, oldShape, newShape);
                    actionNode.execute(storage, name, value);
                    return storage;
                } catch (IncompatibleLocationException ex) {
                    /* Our guards ensure that the value can be stored, so this cannot happen. */
                    throw new IllegalStateException(ex);
                }
            }

            /**
             * The generic case is used if the number of shapes accessed overflows the limit of the
             * polymorphic inline cache.
             */
            @TruffleBoundary
            @Specialization(replaces = {"doDynamicObjectExistingCached", "doDynamicObjectNewCached"}, guards = {"storage.getStore().getShape().isValid()", "!exceedsLimit(storage)"})
            protected HashingStorage doDynamicObjectUncached(DynamicObjectStorage storage, String name, Object value) {
                storage.getStore().define(name, value);
                actionNode.execute(storage, name, value);
                return storage;
            }

            @Specialization(guards = {"storage.getStore().getShape().isValid()", "exceedsLimit(storage)"})
            protected HashingStorage doDynamicObjectGeneralize(FastDictStorage storage, String name, Object value) {
                HashingStorage newStorage = switchToEconomicMap(storage);
                newStorage.setItem(name, value, getEquivalence());
                return newStorage;
            }

            @TruffleBoundary
            @Specialization(guards = {"!storage.getStore().getShape().isValid()"})
            protected HashingStorage doDynamicObjectUpdateShape(DynamicObjectStorage storage, String name, Object value) {
                storage.getStore().updateShape();
                return doDynamicObjectUncached(storage, name, value);
            }
        }

        private static final class DynamicObjectSetItemUncachedNode extends DynamicObjectSetItemNode {
            private static final DynamicObjectSetItemUncachedNode INSTANCE = new DynamicObjectSetItemUncachedNode();

            @Override
            public HashingStorage execute(DynamicObjectStorage s, Object key, Object val) {
                if (key instanceof String || key instanceof PString) {
                    String skey = key instanceof String ? (String) key : ((PString) key).getValue();
                    if (!exceedsLimit(s)) {
                        DynamicObject store = s.getStore();
                        Shape shape = store.getShape();
                        if (!shape.isValid()) {
                            store.updateShape();
                        }
                        store.define(skey, val);
                        InvalidateMroNode.getUncached().execute(s, skey, val);
                    } else {
                        // switch to economic map
                        Equivalence slowPathEquivalence = HashingStorage.getSlowPathEquivalence(skey);
                        EconomicMapStorage newStorage = EconomicMapStorage.create(s.length() + 1, false);
                        newStorage.addAll(s, slowPathEquivalence);
                        newStorage.setItem(skey, val, slowPathEquivalence);
                        return newStorage;
                    }
                }
                return null;
            }

        }

        public static DynamicObjectSetItemNode create() {
            return DynamicObjectSetItemCachedNodeGen.create();
        }

        public static DynamicObjectSetItemNode getUncached() {
            return DynamicObjectSetItemUncachedNode.INSTANCE;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class GetItemNode extends DictStorageBaseNode {
        protected static final int MAX_DYNAMIC_STORAGES = 3;

        public static GetItemNode create() {
            return GetItemNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, HashingStorage storage, Object key);

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        @SuppressWarnings("unused")
        Object doEmptyStorage(VirtualFrame frame, EmptyStorage storage, Object key,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @CachedLibrary("key") PythonObjectLibrary lib) {
            // n.b.: we need to call the __hash__ function here for the
            // side-effect to comply with Python semantics.
            if (profile.profile(frame == null)) {
                lib.hash(key);
            } else {
                lib.hashWithState(key, PArguments.getThreadState(frame));
            }
            return null;
        }

        // this is just a minor performance optimization
        @Specialization
        static Object doPythonObjectString(PythonObjectDictStorage storage, String key,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            return doDynamicObjectString(storage, key, readKey, profile);
        }

        // this is just a minor performance optimization
        @Specialization
        static Object doPythonObjectPString(PythonObjectDictStorage storage, PString key,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            return doDynamicObjectPString(storage, key, readKey, profile);
        }

        // this will read from the dynamic object
        @Specialization
        static Object doDynamicObjectString(DynamicObjectStorage storage, String key,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object result = readKey.execute(storage.getStore(), key);
            return profile.profile(result == PNone.NO_VALUE) ? null : result;
        }

        @Specialization
        static Object doDynamicObjectPString(DynamicObjectStorage storage, PString key,
                        @Shared("readKey") @Cached ReadAttributeFromDynamicObjectNode readKey,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object result = readKey.execute(storage.getStore(), key);
            return profile.profile(result == PNone.NO_VALUE) ? null : result;
        }

        // this must read from the non-dynamic object storage
        @Specialization(guards = {"!isString(key)", "lib.isHashable(key)"}, limit = "1")
        Object doDynamicStorage(PythonObjectHybridDictStorage s, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return s.getItem(key, getEquivalence());
        }

        protected static boolean isPythonObjectHybridStorage(DynamicObjectStorage s) {
            return s instanceof PythonObjectHybridDictStorage;
        }

        // any dynamic object storage that isn't hybridized cannot store
        // non-string keys
        @Specialization(guards = {"!isString(key)", "lib.isHashable(key)", "!isPythonObjectHybridStorage(s)"}, limit = "1")
        @SuppressWarnings("unused")
        Object doDynamicStorage(DynamicObjectStorage s, Object key,
                        @CachedLibrary("key") PythonObjectLibrary lib) {
            return null;
        }

        @Specialization
        Object doKeywordsString(KeywordsStorage storage, String key) {
            return storage.getItem(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        Object doKeywordsString(KeywordsStorage storage, PString key) {
            return storage.getItem(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("unused")
        Object doKeywordsObject(KeywordsStorage storage, Object key,
                        @CachedLibrary("key") PythonObjectLibrary lib) {
            return null;
        }

        @Specialization
        Object doLocalsString(LocalsStorage storage, String key) {
            return storage.getItem(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        Object doLocalsString(LocalsStorage storage, PString key) {
            return storage.getItem(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        Object doLocalsObject(VirtualFrame frame, LocalsStorage storage, PString key,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupHash,
                        @Cached LookupAttributeInMRONode.Dynamic lookupStringHash,
                        @Cached GetItemNode recursiveNode,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            if (lookupHash.execute(key, __HASH__) == lookupStringHash.execute(PythonBuiltinClassType.PString, __HASH__)) {
                return recursiveNode.execute(frame, storage, key.getValue());
            }
            CompilerDirectives.transferToInterpreter();
            // see GR-17389
            throw new RuntimeException("String subclasses with custom hash in dict not implemented.");
        }

        @Specialization(guards = {"!isJavaString(key)", "lib.isHashable(key)"}, limit = "1")
        @SuppressWarnings("unused")
        Object doLocalsObject(LocalsStorage storage, Object key,
                        @CachedLibrary("key") PythonObjectLibrary lib) {
            return null;
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        Object doGeneric(EconomicMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return storage.getItem(key, getEquivalence());
        }

        @Specialization(guards = "lib.isHashable(key)", limit = "1")
        Object doGeneric(HashMapStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            return storage.getItem(key, getEquivalence());
        }

        @Specialization(guards = "!lib.isHashable(key)", limit = "1")
        Object doUnhashable(@SuppressWarnings("unused") HashingStorage storage, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib) {
            throw unhashable(key);
        }
    }

    public abstract static class GetItemInteropNode extends PNodeWithContext {

        private static final GetItemUncachedNode UNCACHED = new GetItemUncachedNode();

        public static GetItemInteropNode create() {
            return new GetItemCachedNode();
        }

        public static GetItemInteropNode getUncached() {
            return UNCACHED;
        }

        public abstract Object executeWithGlobalState(HashingStorage storage, Object key);

        @NodeInfo(cost = NodeCost.NONE)
        private static final class GetItemCachedNode extends GetItemInteropNode {
            @Child private GetItemNode getItemNode = GetItemNode.create();

            @Override
            public Object executeWithGlobalState(HashingStorage storage, Object key) {
                return getItemNode.execute(null, storage, key);
            }
        }

        private static final class GetItemUncachedNode extends GetItemInteropNode {

            @Override
            @TruffleBoundary
            public Object executeWithGlobalState(HashingStorage storage, Object key) {
                return storage.getItem(key, HashingStorage.getSlowPathEquivalence(key));
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.MEGAMORPHIC;
            }

            @Override
            public boolean isAdoptable() {
                return false;
            }
        }
    }

    public abstract static class EqualsNode extends DictStorageBaseNode {

        @Child private GetItemNode leftItemNode;
        @Child private GetItemNode rightItemNode;

        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        @Specialization(guards = "selfStorage.length() == other.length()")
        @SuppressWarnings("try")
        boolean doLocals(VirtualFrame frame, LocalsStorage selfStorage, LocalsStorage other) {
            if (selfStorage.getFrame().getFrameDescriptor() == other.getFrame().getFrameDescriptor()) {
                PythonContext context = getContextRef().get();
                Object state = IndirectCallContext.enter(frame, context, this);
                try {
                    return equalsGeneric(selfStorage, other);
                } finally {
                    IndirectCallContext.exit(frame, context, state);
                }
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        @SuppressWarnings("try")
        boolean doGeneric(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return equalsGeneric(selfStorage, other);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean doFallback(HashingStorage selfStorage, HashingStorage other) {
            return false;
        }

        @TruffleBoundary
        private boolean equalsGeneric(HashingStorage selfStorage, HashingStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    Object leftItem = getLeftItemNode().execute(null, selfStorage, key);
                    Object rightItem = getRightItemNode().execute(null, other, key);
                    if (rightItem == null || !getEquivalence().equals(leftItem, rightItem)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private GetItemNode getLeftItemNode() {
            if (leftItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                leftItemNode = insert(GetItemNode.create());
            }
            return leftItemNode;
        }

        private GetItemNode getRightItemNode() {
            if (rightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightItemNode = insert(GetItemNode.create());
            }
            return rightItemNode;
        }

        public static EqualsNode create() {
            return EqualsNodeGen.create();
        }
    }

    public abstract static class KeysEqualsNode extends DictStorageBaseNode {
        @Child private ContainsKeyNode rightContainsKeyNode;

        public abstract boolean execute(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other);

        private ContainsKeyNode getRightContainsKeyNode() {
            if (rightContainsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightContainsKeyNode = insert(ContainsKeyNode.create());
            }
            return rightContainsKeyNode;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(LocalsStorage selfStorage, LocalsStorage other) {
            if (selfStorage.getFrame().getFrameDescriptor() == other.getFrame().getFrameDescriptor()) {
                return doKeywordsString(selfStorage, other);
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(VirtualFrame frame, DynamicObjectStorage selfStorage, DynamicObjectStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    if (!getRightContainsKeyNode().execute(frame, other, key)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Specialization(guards = "selfStorage.length() == other.length()")
        boolean doKeywordsString(VirtualFrame frame, HashingStorage selfStorage, HashingStorage other) {
            if (selfStorage.length() == other.length()) {
                Iterable<Object> keys = selfStorage.keys();
                for (Object key : keys) {
                    if (!getRightContainsKeyNode().execute(frame, other, key)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean doGeneric(HashingStorage selfStorage, HashingStorage other) {
            return false;
        }

        public static KeysEqualsNode create() {
            return KeysEqualsNodeGen.create();
        }
    }

    public abstract static class DelItemNode extends DictStorageBaseNode {

        public abstract boolean execute(VirtualFrame frame, PHashingCollection dict, HashingStorage dictStorage, Object key);

        @SuppressWarnings("unused")
        @Specialization
        protected boolean doEmptyStorage(PHashingCollection container, EmptyStorage storage, Object value) {
            return false;
        }

        @Specialization
        protected boolean doKeywordsString(PHashingCollection container, KeywordsStorage storage, String key) {
            if (storage.hasKey(key, DEFAULT_EQIVALENCE)) {
                DynamicObjectStorage newStorage = switchToFastDictStorage(storage);
                newStorage.remove(key, DEFAULT_EQIVALENCE);
                container.setDictStorage(newStorage);
                return true;
            }
            return false;
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doKeywordsPString(PHashingCollection container, KeywordsStorage storage, PString key) {
            if (storage.hasKey(key.getValue(), DEFAULT_EQIVALENCE)) {
                DynamicObjectStorage newStorage = switchToFastDictStorage(storage);
                newStorage.remove(key.getValue(), DEFAULT_EQIVALENCE);
                container.setDictStorage(newStorage);
                return true;
            }
            return false;
        }

        @Specialization
        protected boolean doLocalsString(PHashingCollection container, LocalsStorage storage, String key) {
            if (storage.hasKey(key, DEFAULT_EQIVALENCE)) {
                DynamicObjectStorage newStorage = switchToFastDictStorage(storage);
                newStorage.remove(key, DEFAULT_EQIVALENCE);
                container.setDictStorage(newStorage);
                return true;
            }
            return false;
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doLocalsPString(PHashingCollection container, LocalsStorage storage, PString key) {
            if (storage.hasKey(key.getValue(), DEFAULT_EQIVALENCE)) {
                DynamicObjectStorage newStorage = switchToFastDictStorage(storage);
                newStorage.remove(key.getValue(), DEFAULT_EQIVALENCE);
                container.setDictStorage(newStorage);
                return true;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doKeywords(PHashingCollection container, KeywordsStorage storage, Object key) {
            // again: nothing to do since keywords storage can only contain String keys
            return false;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doLocals(PHashingCollection container, LocalsStorage storage, Object key) {
            return false;
        }

        @Specialization
        protected boolean doDynamicObjectString(@SuppressWarnings("unused") PDict container, DynamicObjectStorage storage, String key) {
            return storage.remove(key, DEFAULT_EQIVALENCE);
        }

        @Specialization
        protected boolean doDynamicObjectString(@SuppressWarnings("unused") PSet container, DynamicObjectStorage storage, String key) {
            return storage.remove(key, DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doDynamicObjectPString(@SuppressWarnings("unused") PDict container, DynamicObjectStorage storage, PString key) {
            return storage.remove(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = "wrappedString(key)")
        protected boolean doDynamicObjectPString(@SuppressWarnings("unused") PSet container, DynamicObjectStorage storage, PString key) {
            return storage.remove(key.getValue(), DEFAULT_EQIVALENCE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isJavaString(key)")
        protected boolean doDynamicObject(PHashingCollection container, DynamicObjectStorage storage, Object key) {
            return false;
        }

        @Specialization
        @SuppressWarnings("try")
        protected boolean doEconomicMap(VirtualFrame frame, @SuppressWarnings("unused") PHashingCollection container, EconomicMapStorage storage, Object key) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.remove(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization
        @SuppressWarnings("try")
        protected boolean doHashMap(VirtualFrame frame, @SuppressWarnings("unused") PHashingCollection container, HashMapStorage storage, Object key) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.remove(key, getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static DelItemNode create() {
            return DelItemNodeGen.create();
        }
    }

    public abstract static class CopyNode extends DictStorageBaseNode {

        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage storage);

        @Specialization(guards = "isImmutableStorage(storage)")
        HashingStorage doImmutable(HashingStorage storage) {
            // immutable, just reuse
            return storage;
        }

        @Specialization
        HashingStorage doDynamicObject(DynamicObjectStorage storage) {
            return storage.copy(DEFAULT_EQIVALENCE);
        }

        @Specialization(guards = {"!isImmutableStorage(storage)", "!isDynamicObjectStorage(storage)"})
        @SuppressWarnings("try")
        HashingStorage doGeneric(VirtualFrame frame, HashingStorage storage) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return storage.copy(getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        protected static boolean isImmutableStorage(HashingStorage storage) {
            return storage instanceof EmptyStorage || storage instanceof KeywordsStorage || storage instanceof LocalsStorage;
        }

        protected static boolean isDynamicObjectStorage(HashingStorage storage) {
            return storage instanceof DynamicObjectStorage;
        }

        public static CopyNode create() {
            return CopyNodeGen.create();
        }
    }

    public static class IntersectNode extends Node {

        @Child private ContainsKeyNode containsKeyNode;
        @Child private SetItemNode setItemNode;

        public HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right) {
            HashingStorage newStorage = EconomicMapStorage.create(false);
            if (left.length() != 0 && right.length() != 0) {
                if (containsKeyNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    containsKeyNode = insert(ContainsKeyNode.create());
                }
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(SetItemNode.create());
                }

                for (Object leftKey : left.keys()) {
                    if (containsKeyNode.execute(frame, right, leftKey)) {
                        newStorage = setItemNode.execute(frame, newStorage, leftKey, PNone.NO_VALUE);
                    }
                }
            }
            return newStorage;
        }

        public static IntersectNode create() {
            return new IntersectNode();
        }
    }

    public abstract static class UnionNode extends DictStorageBaseNode {

        protected final boolean setUnion;

        public UnionNode(boolean setUnion) {
            this.setUnion = setUnion;
        }

        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization(guards = "setUnion")
        @SuppressWarnings("try")
        public HashingStorage doGenericSet(VirtualFrame frame, HashingStorage left, HashingStorage right) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(setUnion);
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                for (Object key : left.keys()) {
                    newStorage.setItem(key, PNone.NO_VALUE, getEquivalence());
                }
                for (Object key : right.keys()) {
                    newStorage.setItem(key, PNone.NO_VALUE, getEquivalence());
                }
                return newStorage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(guards = "!setUnion")
        @SuppressWarnings("try")
        public HashingStorage doGeneric(VirtualFrame frame, HashingStorage left, HashingStorage right) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(setUnion);
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                newStorage.addAll(left, getEquivalence());
                newStorage.addAll(right, getEquivalence());
                return newStorage;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        public static UnionNode create() {
            return create(false);
        }

        public static UnionNode create(boolean setUnion) {
            return UnionNodeGen.create(setUnion);
        }
    }

    public static class ExclusiveOrNode extends Node {
        @Child private ContainsKeyNode containsKeyNode;
        @Child private SetItemNode setItemNode;

        public HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right) {
            HashingStorage newStorage = EconomicMapStorage.create(false);
            if (left.length() != 0 && right.length() != 0) {
                if (containsKeyNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    containsKeyNode = insert(ContainsKeyNode.create());
                }
                if (setItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setItemNode = insert(SetItemNode.create());
                }

                for (Object leftKey : left.keys()) {
                    if (!containsKeyNode.execute(frame, right, leftKey)) {
                        newStorage = setItemNode.execute(frame, newStorage, leftKey, PNone.NO_VALUE);
                    }
                }
                for (Object rightKey : right.keys()) {
                    if (!containsKeyNode.execute(frame, left, rightKey)) {
                        newStorage = setItemNode.execute(frame, newStorage, rightKey, PNone.NO_VALUE);
                    }
                }
            }
            return newStorage;
        }

        public static ExclusiveOrNode create() {
            return new ExclusiveOrNode();
        }
    }

    public abstract static class KeysIsSubsetNode extends DictStorageBaseNode {
        protected static final int MAX_STORAGES = HashingStorageNodes.MAX_STORAGES;

        public abstract boolean execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        @Specialization(limit = "MAX_STORAGES", guards = {"left.getClass() == leftClass", "right.getClass() == rightClass"})
        public boolean isSubsetCached(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @Cached("left.getClass()") Class<? extends HashingStorage> leftClass,
                        @Cached("right.getClass()") Class<? extends HashingStorage> rightClass,
                        @Cached("create()") ContainsKeyNode containsKeyNode,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(leftClass.cast(left).length() > rightClass.cast(right).length())) {
                return false;
            }

            for (Object leftKey : leftClass.cast(left).keys()) {
                if (!containsKeyNode.execute(frame, right, leftKey)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(replaces = "isSubsetCached")
        public boolean isSubset(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @Cached("create()") ContainsKeyNode containsKeyNode,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(left.length() > right.length())) {
                return false;
            }

            for (Object leftKey : left.keys()) {
                if (!containsKeyNode.execute(frame, right, leftKey)) {
                    return false;
                }
            }
            return true;
        }

        public static KeysIsSubsetNode create() {
            return KeysIsSubsetNodeGen.create();
        }
    }

    public static class KeysIsSupersetNode extends Node {
        @Child KeysIsSubsetNode isSubsetNode;

        public boolean execute(VirtualFrame frame, HashingStorage left, HashingStorage right) {
            if (isSubsetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubsetNode = insert(KeysIsSubsetNode.create());
            }

            return isSubsetNode.execute(frame, right, left);
        }

        public static KeysIsSupersetNode create() {
            return new KeysIsSupersetNode();
        }
    }

    public abstract static class DiffNode extends DictStorageBaseNode {
        protected static final int MAX_STORAGES = HashingStorageNodes.MAX_STORAGES;

        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage left, HashingStorage right);

        protected boolean isEmpty(Class<? extends HashingStorage> theClass, HashingStorage s) {
            return theClass.cast(s).length() == 0;
        }

        @Specialization(limit = "MAX_STORAGES", guards = {"left.getClass() == leftClass", "isEmpty(leftClass, left)"})
        @SuppressWarnings("unused")
        public HashingStorage doLeftEmpty(HashingStorage left, HashingStorage right,
                        @SuppressWarnings("unused") @Cached("left.getClass()") Class<? extends HashingStorage> leftClass) {
            return EconomicMapStorage.create(false);
        }

        @Specialization(limit = "MAX_STORAGES", guards = {"left.getClass() == leftClass", "right.getClass() == rightClass"})
        @SuppressWarnings("try")
        public HashingStorage doNonEmptyCached(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @Cached("left.getClass()") Class<? extends HashingStorage> leftClass,
                        @Cached("right.getClass()") Class<? extends HashingStorage> rightClass,
                        @Cached("create()") ContainsKeyNode containsKeyNode,
                        @Cached BranchProfile leftEmpty,
                        @Cached BranchProfile rightEmpty,
                        @Cached BranchProfile neitherEmpty,
                        @Cached("create()") SetItemNode setItemNode) {
            if (leftClass.cast(left).length() == 0) {
                leftEmpty.enter();
                return EconomicMapStorage.create(false);
            }
            if (rightClass.cast(right).length() == 0) {
                rightEmpty.enter();
                PythonContext context = getContextRef().get();
                Object state = IndirectCallContext.enter(frame, context, this);
                try {
                    return leftClass.cast(left).copy(getEquivalence());
                } finally {
                    IndirectCallContext.exit(frame, context, state);
                }
            }
            neitherEmpty.enter();
            HashingStorage newStorage = EconomicMapStorage.create(false);
            for (Object leftKey : leftClass.cast(left).keys()) {
                if (!containsKeyNode.execute(frame, right, leftKey)) {
                    newStorage = setItemNode.execute(frame, newStorage, leftKey, PNone.NO_VALUE);
                }
            }
            return newStorage;
        }

        @Specialization(limit = "MAX_STORAGES", guards = {"right.getClass() == rightClass", "isEmpty(rightClass, right)"})
        @SuppressWarnings("try")
        public HashingStorage doRightEmpty(VirtualFrame frame, HashingStorage left, @SuppressWarnings("unused") HashingStorage right,
                        @SuppressWarnings("unused") @Cached("right.getClass()") Class<? extends HashingStorage> rightClass) {
            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return left.copy(getEquivalence());
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(replaces = "doNonEmptyCached")
        public HashingStorage doNonEmpty(VirtualFrame frame, HashingStorage left, HashingStorage right,
                        @Cached("create()") ContainsKeyNode containsKeyNode,
                        @Cached("create()") SetItemNode setItemNode) {

            HashingStorage newStorage = EconomicMapStorage.create(false);
            for (Object leftKey : left.keys()) {
                if (!containsKeyNode.execute(frame, right, leftKey)) {
                    newStorage = setItemNode.execute(frame, newStorage, leftKey, PNone.NO_VALUE);
                }
            }
            return newStorage;
        }

        public static DiffNode create() {
            return DiffNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class LenNode extends Node {

        protected static final int MAX_STORAGES = HashingStorageNodes.MAX_STORAGES;

        public abstract int execute(HashingStorage s);

        @Specialization(limit = "MAX_STORAGES", guards = "s.getClass() == cachedClass")
        static int doCached(HashingStorage s,
                        @Cached("s.getClass()") Class<? extends HashingStorage> cachedClass) {
            return cachedClass.cast(s).length();
        }

        @Specialization(replaces = "doCached")
        static int doGeneric(HashingStorage s) {
            return s.length();
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }

        public static LenNode getUncached() {
            return LenNodeGen.getUncached();
        }
    }

    private static final class CastPStringToJavaStringNode extends Node {

        private final ValueProfile profile = ValueProfile.createClassProfile();

        public String execute(PString obj) {
            CharSequence profiled = profile.profile(obj.getCharSequence());
            if (profiled instanceof String) {
                return (String) profiled;
            } else if (profiled instanceof LazyString) {
                return ((LazyString) profiled).toString();
            }
            return generic(profiled);
        }

        @TruffleBoundary
        private static String generic(CharSequence profiled) {
            return profiled.toString();
        }

        public static CastPStringToJavaStringNode create() {
            return new CastPStringToJavaStringNode();
        }

    }
}
