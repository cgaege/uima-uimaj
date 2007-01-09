/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.cas.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeNameSpace;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.admin.CASAdminException;
import org.apache.uima.cas.admin.TypeSystemMgr;
import org.apache.uima.internal.util.IntVector;
import org.apache.uima.internal.util.StringToIntMap;
import org.apache.uima.internal.util.SymbolTable;
import org.apache.uima.internal.util.rb_trees.IntRedBlackTree;
import org.apache.uima.internal.util.rb_trees.RedBlackTree;

/**
 * Type system implementation.
 * 
 */
public class TypeSystemImpl implements TypeSystemMgr, LowLevelTypeSystem {

  private static class ListIterator implements Iterator {

    private final List list;

    private final int len;

    private int pos = 0;

    private ListIterator(List list, int max) {
      super();
      this.list = list;
      this.len = (max < list.size()) ? max : list.size();
    }

    public boolean hasNext() {
      return this.pos < this.len;
    }

    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Object o = this.list.get(this.pos);
      ++this.pos;
      return o;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  // static maps ok for now - only built-in mappings stored here
  // which are the same for all type system instances
  private static HashMap arrayComponentTypeNameMap = new HashMap();

  private static HashMap arrayTypeComponentNameMap = new HashMap();

  private static final String arrayTypeSuffix = "[]";

  static {
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_TOP, CAS.TYPE_NAME_FS_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_BOOLEAN, CAS.TYPE_NAME_BOOLEAN_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_BYTE, CAS.TYPE_NAME_BYTE_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_SHORT, CAS.TYPE_NAME_SHORT_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_INTEGER, CAS.TYPE_NAME_INTEGER_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_FLOAT, CAS.TYPE_NAME_FLOAT_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_LONG, CAS.TYPE_NAME_LONG_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_DOUBLE, CAS.TYPE_NAME_DOUBLE_ARRAY);
    arrayComponentTypeNameMap.put(CAS.TYPE_NAME_STRING, CAS.TYPE_NAME_STRING_ARRAY);
  }

  static {
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_FS_ARRAY, CAS.TYPE_NAME_TOP);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_BOOLEAN_ARRAY, CAS.TYPE_NAME_BOOLEAN);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_BYTE_ARRAY, CAS.TYPE_NAME_BYTE);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_SHORT_ARRAY, CAS.TYPE_NAME_SHORT);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_INTEGER_ARRAY, CAS.TYPE_NAME_INTEGER);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_FLOAT_ARRAY, CAS.TYPE_NAME_FLOAT);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_LONG_ARRAY, CAS.TYPE_NAME_LONG);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_DOUBLE_ARRAY, CAS.TYPE_NAME_DOUBLE);
    arrayTypeComponentNameMap.put(CAS.TYPE_NAME_STRING_ARRAY, CAS.TYPE_NAME_STRING);
  }

  // Current implementation has online update. Look-up could be made
  // more efficient by computing some tables, but the assumption is
  // that the type system will not be queried often enough to justify
  // the effort.

  private SymbolTable typeNameST; // Symbol table of type names

  // Symbol table of feature names, containing only one entry per feature,
  // i.e.,
  // its normal form.
  private SymbolTable featureNameST;

  // A map from the full space of feature names to feature codes. A feature
  // may
  // be know by many different names (one for each subtype of the type the
  // feature is declared on).
  private StringToIntMap featureMap;

  private ArrayList tree; // Vector of IntVectors encoding type tree

  private ArrayList subsumes; // Vector of BitSets for subsumption relation

  private IntVector intro;

  // Indicates which type introduces a feature (domain)
  private IntVector featRange; // Indicates range type of features

  private ArrayList approp; // For each type, an IntVector of appropriate

  // features

  // Code of root of hierarchy (will be 1 with current implementation)
  private int top;

  // A vector of TypeImpl objects.
  private ArrayList types;

  // A vector of FeatureImpl objects.
  private ArrayList features;

  // List of parent types.
  private final IntVector parents;

  // String sets for string subtypes.
  private final ArrayList stringSets;

  
  // This map contains an entry for every subtype of the string type.  The value is a pointer into
  // the stringSets array list.
  private final IntRedBlackTree stringSetMap;

  // For each type, remember of an array of this component type has already
  // been created.
  private final IntRedBlackTree componentToArrayTypeMap;

  // A mapping from array types to their component types.
  private final IntRedBlackTree arrayToComponentTypeMap;

  // A mapping from array type codes to array type objects.
  private final RedBlackTree arrayCodeToTypeMap;

  // Is the type system locked?
  private boolean locked = false;

  // Must be able to lock type system info in the CAS, so need a handle to
  // embedding CAS.
  // Deleted (MIS 6/06) - Type Systems shared by many CASes, can't point to
  // one.
  // private CASImpl cas;

  private static final int LEAST_TYPE_CODE = 1;

  // private static final int INVALID_TYPE_CODE = 0;
  private static final int LEAST_FEATUE_CODE = 1;

  // private int topTypeCode;
  private int booleanTypeCode = UNKNOWN_TYPE_CODE;

  private int byteTypeCode = UNKNOWN_TYPE_CODE;

  private int shortTypeCode = UNKNOWN_TYPE_CODE;

  private int intTypeCode = UNKNOWN_TYPE_CODE;

  private int floatTypeCode = UNKNOWN_TYPE_CODE;

  private int longTypeCode = UNKNOWN_TYPE_CODE;

  private int doubleTypeCode = UNKNOWN_TYPE_CODE;

  private int stringTypeCode = UNKNOWN_TYPE_CODE;

  private int arrayBaseTypeCode = UNKNOWN_TYPE_CODE;

  private int booleanArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int byteArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int shortArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int intArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int floatArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int longArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int doubleArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int stringArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int fsArrayTypeCode = UNKNOWN_TYPE_CODE;

  private int numCommittedTypes = 0;

  /**
   * Default constructor.
   * 
   * @deprecated Use 0 arg constructor. Type Systems are shared by many CASes, and can't point to
   *             one. Change also your possible calls to ts.commit() - see comment on that method.
   */
  public TypeSystemImpl(CASImpl cas) {
    this();
  }

  public TypeSystemImpl() {
    super();
    // Changed numbering to start at 1. Hope this doesn't break
    // anything. If it does, I know who's fault it is...
    this.typeNameST = new SymbolTable(1);
    this.featureNameST = new SymbolTable(1);
    this.featureMap = new StringToIntMap();
    // In each Vector, add null as first element, since we start
    // counting at 1.
    this.tree = new ArrayList();
    this.tree.add(null);
    this.subsumes = new ArrayList();
    this.subsumes.add(null);
    this.intro = new IntVector();
    this.intro.add(0);
    this.featRange = new IntVector();
    this.featRange.add(0);
    this.approp = new ArrayList();
    this.approp.add(null);
    this.types = new ArrayList();
    this.types.add(null);
    this.features = new ArrayList();
    this.features.add(null);
    this.stringSets = new ArrayList();
    this.stringSetMap = new IntRedBlackTree();
    this.componentToArrayTypeMap = new IntRedBlackTree();
    this.arrayToComponentTypeMap = new IntRedBlackTree();
    this.arrayCodeToTypeMap = new RedBlackTree();
    this.parents = new IntVector();
    this.parents.add(0);
  }

  // Some implementation helpers for users of the type system.
  final int getSmallestType() {
    return LEAST_TYPE_CODE;
  }

  final int getSmallestFeature() {
    return LEAST_FEATUE_CODE;
  }

  final int getTypeArraySize() {
    return getNumberOfTypes() + getSmallestType();
  }

  public Vector getIntroFeatures(Type type) {
    Vector feats = new Vector();
    List appropFeats = type.getFeatures();
    final int max = appropFeats.size();
    Feature feat;
    for (int i = 0; i < max; i++) {
      feat = (Feature) appropFeats.get(i);
      if (feat.getDomain() == type) {
        feats.add(feat);
      }
    }
    return feats;
  }

  public Type getParent(Type t) {
    return ((TypeImpl) t).getSuperType();
  }

  public int ll_getParentType(int typeCode) {
    return this.parents.get(typeCode);
  }

  int ll_computeArrayParentFromComponentType(int componentType) {
    if (ll_isPrimitiveType(componentType) ||
    // note: not using this.top - until we can confirm this is set
            // in all cases
            (ll_getTypeForCode(componentType).getName().equals(CAS.TYPE_NAME_TOP))) {
      return ll_getCodeForTypeName(CAS.TYPE_NAME_ARRAY_BASE);
    }
    // is a subtype of FSArray.
    // note: not using this.fsArray - until we can confirm this is set in
    // all cases
    return ll_getCodeForTypeName(CAS.TYPE_NAME_FS_ARRAY);
    // return ll_getArrayType(ll_getParentType(componentType));
  }

  /**
   * Check if feature is appropriate for type (i.e., type is subsumed by domain type of feature).
   */
  public boolean isApprop(int type, int feat) {
    return subsumes(intro(feat), type);
  }

  public final int getLargestTypeCode() {
    return getNumberOfTypes();
  }

  public boolean isType(int type) {
    return ((type > 0) && (type <= getLargestTypeCode()));
  }

  /**
   * Get a type object for a given code.
   * 
   * @param typeCode
   *          The code of the type.
   * @return A type object, or <code>null</code> if no such type exists.
   */
  public Type getType(int typeCode) {
    return (Type) this.types.get(typeCode);
  }

  public String getTypeName(int typeCode) {
    return this.typeNameST.getSymbol(typeCode);
  }

  public String getFeatureName(int featCode) {
    return this.featureNameST.getSymbol(featCode);
  }

  /**
   * Get a type object for a given name.
   * 
   * @param typeName
   *          The name of the type.
   * @return A type object, or <code>null</code> if no such type exists.
   */
  public Type getType(String typeName) {
    final int typeCode = ll_getCodeForTypeName(typeName);
    if (typeCode < LEAST_TYPE_CODE) {
      return null;
    }
    return (Type) this.types.get(typeCode);
  }

  /**
   * Get an feature object for a given code.
   * 
   * @param featCode
   *          The code of the feature.
   * @return A feature object, or <code>null</code> if no such feature exists.
   */
  public Feature getFeature(int featCode) {
    return (Feature) this.features.get(featCode);
  }

  /**
   * Get an feature object for a given name.
   * 
   * @param featureName
   *          The name of the feature.
   * @return An feature object, or <code>null</code> if no such feature exists.
   */
  public Feature getFeatureByFullName(String featureName) {
    if (!this.featureMap.containsKey(featureName)) {
      return null;
    }
    final int featCode = this.featureMap.get(featureName);
    return (Feature) this.features.get(featCode);
  }

  private static final String getArrayTypeName(String typeName) {
    if (arrayComponentTypeNameMap.containsKey(typeName)) {
      return (String) arrayComponentTypeNameMap.get(typeName);
    }
    return typeName + arrayTypeSuffix;
  }

  private static final String getBuiltinArrayComponent(String typeName) {
    if (arrayTypeComponentNameMap.containsKey(typeName)) {
      return (String) arrayTypeComponentNameMap.get(typeName);
    }
    return null;
  }

  /**
   * Add a new type to the type system.
   * 
   * @param typeName
   *          The name of the new type.
   * @param mother
   *          The type node under which the new type should be attached.
   * @return The new type, or <code>null</code> if <code>typeName</code> is already in use.
   */
  public Type addType(String typeName, Type mother) throws CASAdminException {
    if (this.locked) {
      throw new CASAdminException(CASAdminException.TYPE_SYSTEM_LOCKED);
    }
    if (mother.isInheritanceFinal()) {
      CASAdminException e = new CASAdminException(CASAdminException.TYPE_IS_INH_FINAL);
      e.addArgument(mother.getName());
      throw e;
    }
    // Check type name syntax.
    String componentTypeName = getBuiltinArrayComponent(typeName);
    if (componentTypeName != null) {
      return getArrayType(getType(componentTypeName));
    }
    checkTypeSyntax(typeName);
    final int typeCode = this.addType(typeName, ((TypeImpl) mother).getCode());
    if (typeCode < this.typeNameST.getStart()) {
      return null;
    }
    return (Type) this.types.get(typeCode);
  }

  /**
   * Method checkTypeSyntax.
   * 
   * @param typeName
   */
  private void checkTypeSyntax(String name) throws CASAdminException {
    if (!TypeSystemUtils.isTypeName(name)) {
      CASAdminException e = new CASAdminException(CASAdminException.BAD_TYPE_SYNTAX);
      e.addArgument(name);
      throw e;
    }
  }

  int addType(String name, int superType) {
    return addType(name, superType, false);
  }

  /**
   * Internal code for adding a new type. Warning: no syntax check on type name, must be done by
   * caller. This method is not private because it's used by the serialization code.
   */
  int addType(String name, int superType, boolean isStringType) {
    if (this.typeNameST.contains(name)) {
      return -1;
    }
    // assert (isType(superType)); //: "Supertype is not a known type:
    // "+superType;
    // Add the new type to the symbol table.
    final int type = this.typeNameST.set(name);
    // Create space for new type.
    newType();
    // Add an edge to the tree.
    ((IntVector) this.tree.get(superType)).add(type);
    // Update subsumption relation.
    updateSubsumption(type, superType);
    // Add inherited features.
    final IntVector superApprop = (IntVector) this.approp.get(superType);
    // superApprop.add(0);
    final IntVector typeApprop = (IntVector) this.approp.get(type);
    // typeApprop.add(0);
    final int max = superApprop.size();
    int featCode;
    for (int i = 0; i < max; i++) {
      featCode = superApprop.get(i);
      typeApprop.add(featCode);
      // Add inherited feature names.
      String feat = name + TypeSystem.FEATURE_SEPARATOR + getFeature(featCode).getShortName();
      // System.out.println("Adding name: " + feat);
      this.featureMap.put(feat, featCode);
    }
    TypeImpl t;
    if (isStringType) {
      final int stringSetCode = this.stringSets.size();
      this.stringSetMap.put(type, stringSetCode);
      t = new StringTypeImpl(name, type, this);
    } else {
      t = new TypeImpl(name, type, this);
    }
    this.types.add(t);
    this.parents.add(superType);
    this.numCommittedTypes = this.types.size();
    return type;
  }

  public Feature addFeature(String featureName, Type domainType, Type rangeType)
          throws CASAdminException {
    return addFeature(featureName, domainType, rangeType, true);
  }

  /**
   * @see TypeSystemMgr#addFeature(String, Type, Type)
   */
  public Feature addFeature(String featureName, Type domainType, Type rangeType,
          boolean multipleReferencesAllowed) throws CASAdminException {
    // assert(featureName != null);
    // assert(domainType != null);
    // assert(rangeType != null);
    if (this.locked) {
      throw new CASAdminException(CASAdminException.TYPE_SYSTEM_LOCKED);
    }
    Feature f = domainType.getFeatureByBaseName(featureName);
    if (f != null && f.getRange().equals(rangeType)) {
      return f;
    }
    if (domainType.isFeatureFinal()) {
      CASAdminException e = new CASAdminException(CASAdminException.TYPE_IS_FEATURE_FINAL);
      e.addArgument(domainType.getName());
      throw e;
    }
    checkFeatureNameSyntax(featureName);
    final int featCode = this.addFeature(featureName, ((TypeImpl) domainType).getCode(),
            ((TypeImpl) rangeType).getCode(), multipleReferencesAllowed);
    if (featCode < this.featureNameST.getStart()) {
      return null;
    }
    return (Feature) this.features.get(featCode);
  }

  /**
   * Method checkFeatureNameSyntax.
   */
  private void checkFeatureNameSyntax(String name) throws CASAdminException {
    if (!TypeSystemUtils.isIdentifier(name)) {
      CASAdminException e = new CASAdminException(CASAdminException.BAD_FEATURE_SYNTAX);
      e.addArgument(name);
      throw e;
    }
  }

  /**
   * Get an iterator over all types, in no particular order.
   * 
   * @return The iterator.
   */
  public Iterator getTypeIterator() {
    Iterator it = new ListIterator(this.types, this.numCommittedTypes);
    // The first element is null, so skip it.
    it.next();
    return it;
  }

  public Iterator getFeatures() {
    Iterator it = this.features.iterator();
    // The first element is null, so skip it.
    it.next();
    return it;
  }

  /**
   * Get the top type, i.e., the root of the type system.
   * 
   * @return The top type.
   */
  public Type getTopType() {
    return (Type) this.types.get(this.top);
  }

  /**
   * Return the list of all types subsumed by the input type. Note: the list does not include the
   * type itself.
   * 
   * @param type
   *          Input type.
   * @return The list of types subsumed by <code>type</code>.
   */
  public List getProperlySubsumedTypes(Type type) {
    ArrayList subList = new ArrayList();
    Iterator typeIt = getTypeIterator();
    while (typeIt.hasNext()) {
      Type t = (Type) typeIt.next();
      if (type != t && subsumes(type, t)) {
        subList.add(t);
      }
    }
    return subList;
  }

  /**
   * Get a vector of the types directly subsumed by a given type.
   * 
   * @param type
   *          The input type.
   * @return A vector of the directly subsumed types.
   */
  public Vector getDirectlySubsumedTypes(Type type) {
    return new Vector(getDirectSubtypes(type));
  }

  public List getDirectSubtypes(Type type) {
    if (type.isArray()) {
      return new ArrayList();
    }
    ArrayList list = new ArrayList();
    IntVector sub = (IntVector) this.tree.get(((TypeImpl) type).getCode());
    final int max = sub.size();
    for (int i = 0; i < max; i++) {
      list.add(this.types.get(sub.get(i)));
    }
    return list;
  }

  public boolean directlySubsumes(int t1, int t2) {
    IntVector sub = (IntVector) this.tree.get(t1);
    return sub.contains(t2);
  }

  /**
   * Does one type inherit from the other?
   * 
   * @param superType
   *          Supertype.
   * @param subType
   *          Subtype.
   * @return <code>true</code> iff <code>sub</code> inherits from <code>super</code>.
   */
  public boolean subsumes(Type superType, Type subType) {
    // assert(superType != null);
    // assert(subType != null);
    return this.subsumes(((TypeImpl) superType).getCode(), ((TypeImpl) subType).getCode());
  }

  /**
   * Get the code for a feature, given its name.
   */
  public int getFeatureCode(String feat) {
    return ll_getCodeForFeatureName(feat);
  }

  /**
   * Get an array of the appropriate features for this type.
   */
  public int[] ll_getAppropriateFeatures(int type) {
    if (type < LEAST_TYPE_CODE || type > getNumberOfTypes()) {
      return null;
    }
    // We have to copy the array since we don't have const.
    return ((IntVector) this.approp.get(type)).toArrayCopy();
  }

  public int[] getAppropriateFeatures(int type) {
    return ll_getAppropriateFeatures(type);
  }

  /**
   * @return An offset <code>&gt;0</code> if <code>feat</code> exists; <code>0</code>, else.
   */
  int getFeatureOffset(int feat) {
    return ((IntVector) this.approp.get(this.intro.get(feat))).position(feat) + 1;
  }

  /**
   * Get the overall number of features defined in the type system.
   */
  public int getNumberOfFeatures() {
    return this.featureNameST.size();
  }

  /**
   * Get the overall number of types defined in the type system.
   */
  public int getNumberOfTypes() {
    return this.typeNameST.size();
  }

  /**
   * Get the domain type for a feature.
   */
  public int intro(int feat) {
    return this.intro.get(feat);
  }

  /**
   * Get the range type for a feature.
   */
  public int range(int feat) {
    return this.featRange.get(feat);
  }

  // Unification is trivial, since we don't have multiple inheritance.
  public int unify(int t1, int t2) {
    if (this.subsumes(t1, t2)) {
      return t2;
    } else if (this.subsumes(t2, t1)) {
      return t1;
    } else {
      return -1;
    }
  }

  int addFeature(String shortName, int domain, int range) {
    return addFeature(shortName, domain, range, true);
  }

  /**
   * Add a new feature to the type system.
   */
  int addFeature(String shortName, int domain, int range, boolean multiRefsAllowed) {
    // Since we just looked up the domain in the symbol table, we know it
    // exists.
    String name = this.typeNameST.getSymbol(domain) + TypeSystem.FEATURE_SEPARATOR + shortName;
    // Create a list of the domain type and all its subtypes.
    // Type t = getType(domain);
    // if (t == null) {
    // System.out.println("Type is null");
    // }
    List typesLocal = getProperlySubsumedTypes(getType(domain));
    typesLocal.add(getType(domain));
    // For each type, check that the feature doesn't already exist.
    int max = typesLocal.size();
    for (int i = 0; i < max; i++) {
      String featureName = ((Type) typesLocal.get(i)).getName() + FEATURE_SEPARATOR + shortName;
      if (this.featureMap.containsKey(featureName)) {
        // We have already added this feature. If the range of the
        // duplicate
        // feature is identical, we don't do anything and just return.
        // Else,
        // we throw an exception.
        Feature oldFeature = getFeatureByFullName(featureName);
        Type oldDomain = oldFeature.getDomain();
        Type oldRange = oldFeature.getRange();
        if (range == ll_getCodeForType(oldRange)) {
          return -1;
        }
        CASAdminException e = new CASAdminException(CASAdminException.DUPLICATE_FEATURE);
        e.addArgument(shortName);
        e.addArgument(ll_getTypeForCode(domain).getName());
        e.addArgument(ll_getTypeForCode(range).getName());
        e.addArgument(oldDomain.getName());
        e.addArgument(oldRange.getName());
        throw e;
      }
    } // Add name to symbol table.
    int feat = this.featureNameST.set(name);
    // Add entries for all subtypes.
    for (int i = 0; i < max; i++) {
      this.featureMap.put(((Type) typesLocal.get(i)).getName() + FEATURE_SEPARATOR + shortName,
              feat);
    }
    this.intro.add(domain);
    this.featRange.add(range);
    max = this.typeNameST.size();
    for (int i = 1; i <= max; i++) {
      if (subsumes(domain, i)) {
        ((IntVector) this.approp.get(i)).add(feat);
      }
    }
    this.features.add(new FeatureImpl(feat, name, this, multiRefsAllowed));
    return feat;
  }

  /**
   * Add a top type to the (empty) type system.
   */
  public Type addTopType(String name) {
    final int code = this.addTopTypeInternal(name);
    if (code < 1) {
      return null;
    }
    return (Type) this.types.get(code);
  }

  private int addTopTypeInternal(String name) {
    if (this.typeNameST.size() > 0) {
      // System.out.println("Size of type table > 0.");
      return 0;
    } // Add name of top type to symbol table.
    this.top = this.typeNameST.set(name);
    // System.out.println("Size of name table is: " + typeNameST.size());
    // assert (typeNameST.size() == 1);
    // System.out.println("Code of top type is: " + this.top);
    // Create space for top type.
    newType();
    // Make top subsume itself.
    addSubsubsumption(this.top, this.top);
    this.types.add(new TypeImpl(name, this.top, this));
    this.parents.add(LowLevelTypeSystem.UNKNOWN_TYPE_CODE);
    this.numCommittedTypes = this.types.size();
    return this.top;
  }

  /**
   * Check if the first argument subsumes the second
   */
  public boolean subsumes(int superType, int type) {
    return this.ll_subsumes(superType, type);
  }

  private boolean ll_isPrimitiveArrayType(int type) {
    return type == this.floatArrayTypeCode || type == this.intArrayTypeCode
            || type == this.booleanArrayTypeCode || type == this.shortArrayTypeCode
            || type == this.byteArrayTypeCode || type == this.longArrayTypeCode
            || type == this.doubleArrayTypeCode || type == this.stringArrayTypeCode;
  }

  public boolean ll_subsumes(int superType, int type) {
    // Add range check.
    // assert (isType(superType));
    // assert (isType(type));

    // Need special handling for arrays, as they're generated on the fly and
    // not added to the subsumption table.

    // speedup code.
    if (superType == type)
      return true;

    // Special check for subtypes of FSArray.
    if (superType == this.fsArrayTypeCode) {
      return !ll_isPrimitiveArrayType(type) && ll_isArrayType(type);
    }

    // Special check for supertypes of FSArray.  Why do we need this?
    if (type == this.fsArrayTypeCode) {
      return superType == this.top || superType == this.arrayBaseTypeCode;
    }

    // at this point, we could have arrays of other primitive types, or
    // arrays of specific types: xxx[]

    final boolean isSuperArray = ll_isArrayType(superType);
    final boolean isSubArray = ll_isArrayType(type);
    if (isSuperArray) {
      if (isSubArray) {
        // If both types are arrays, simply compare the components.
        return ll_subsumes(ll_getComponentType(superType), ll_getComponentType(type));
      }
      // An array can never subsume a non-array.
      return false;
    } else if (isSubArray) {
      // If the subtype is an array, and the supertype is not, then the
      // supertype must be top, or the abstract array base.
      return ((superType == this.top) || (superType == this.arrayBaseTypeCode));
    }
    return ((BitSet) this.subsumes.get(superType)).get(type);
  }

  private void updateSubsumption(int type, int superType) {
    final int max = this.typeNameST.size();
    for (int i = 1; i <= max; i++) {
      if (subsumes(i, superType)) {
        addSubsubsumption(i, type);
      }
    }
    addSubsubsumption(type, type);
  }

  private void addSubsubsumption(int superType, int type) {
    ((BitSet) this.subsumes.get(superType)).set(type);
  }

  private void newType() {
    // The assumption for the implementation is that new types will
    // always be added at the end.
    this.tree.add(new IntVector());
    this.subsumes.add(new BitSet());
    this.approp.add(new IntVector());
  }

  public SymbolTable getTypeNameST() {
    return this.typeNameST;
  }

  public SymbolTable getFeatureNameST() {
    return this.featureNameST;
  }

  public int getTypeCode(String typeName) {
    return this.typeNameST.get(typeName);
  }

  private final String getTypeString(Type t) {
    return t.getName() + " (" + ll_getCodeForType(t) + ")";
  }

  private final String getFeatureString(Feature f) {
    return f.getName() + " (" + ll_getCodeForFeature(f) + ")";
  }

  /**
   * This writes out the type hierarchy in a human-readable form. Files in this form can be read in
   * by a {@link TypeSystemParser TypeSystemParser}.
   */
  public String toString() {
    // This code is maximally readable, not maximally efficient.
    StringBuffer buf = new StringBuffer();
    // Print top type.
    buf.append("~" + getTypeString(this.getTopType()) + ";\n");
    // Iterate over types and print declarations.
    final int numTypes = this.typeNameST.size();
    Type t;
    for (int i = 2; i <= numTypes; i++) {
      t = this.getType(i);
      buf.append(getTypeString(t) + " < " + getTypeString(this.getParent(t)) + ";\n");
    } // Print feature declarations.
    final int numFeats = this.featureNameST.size();
    Feature f;
    for (int i = 1; i <= numFeats; i++) {
      f = this.getFeature(i);
      buf.append(getFeatureString(f) + ": " + getTypeString(f.getDomain()) + " > "
              + getTypeString(f.getRange()) + ";\n");
    }
    return buf.toString();
  }

  /**
   * @see org.apache.uima.cas.admin.TypeSystemMgr#commit()
   */
  public void commit() {
    if (this.locked == true)
      return; // might be called multiple times, but only need to do once
    this.locked = true;
    initTypeCodes(); // needs to preceed cas.commitTypeSystem()
    // because subsumes depends on it
    // and generator initialization uses subsumes
    this.numCommittedTypes = this.types.size(); // do before
    // cas.commitTypeSystem -
    // because it will call the type system iterator

    // ts should never point to a CAS. Many CASes can share one ts.
    // if (this.cas != null) {
    // this.cas.commitTypeSystem();
    // }
  }

  private final void initTypeCodes() {
    this.booleanTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_BOOLEAN);
    this.byteTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_BYTE);
    this.shortTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_SHORT);
    this.intTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_INTEGER);
    this.floatTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_FLOAT);
    this.longTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_LONG);
    this.doubleTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_DOUBLE);
    this.stringTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_STRING);

    this.arrayBaseTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_ARRAY_BASE);
    this.booleanArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_BOOLEAN_ARRAY);
    this.byteArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_BYTE_ARRAY);
    this.shortArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_SHORT_ARRAY);
    this.intArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_INTEGER_ARRAY);
    this.floatArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_FLOAT_ARRAY);
    this.longArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_LONG_ARRAY);
    this.doubleArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_DOUBLE_ARRAY);
    this.stringArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_STRING_ARRAY);
    this.fsArrayTypeCode = ll_getCodeForTypeName(CAS.TYPE_NAME_FS_ARRAY);
  }

  /**
   * @see org.apache.uima.cas.admin.TypeSystemMgr#isCommitted()
   */
  public boolean isCommitted() {
    return this.locked;
  }

  public void setCommitted(boolean b) {
    this.locked = b;
  }

  /**
   * @see org.apache.uima.cas.TypeSystem#getFeature(java.lang.String)
   * @deprecated
   */
  public Feature getFeature(String featureName) {
    return getFeatureByFullName(featureName);
  }

  /**
   * @see org.apache.uima.cas.admin.TypeSystemMgr#setFeatureFinal(org.apache.uima.cas.Type)
   */
  public void setFeatureFinal(Type type) {
    ((TypeImpl) type).setFeatureFinal();
  }

  /**
   * @see org.apache.uima.cas.admin.TypeSystemMgr#setInheritanceFinal(org.apache.uima.cas.Type)
   */
  public void setInheritanceFinal(Type type) {
    ((TypeImpl) type).setInheritanceFinal();
  }

  /**
   * @see org.apache.uima.cas.admin.TypeSystemMgr#addStringSubtype
   */
  public Type addStringSubtype(String typeName, String[] stringList) throws CASAdminException {
    // final int stringSetCode = this.stringSets.size();
    Type mother = getStringType();
    // Check type name syntax.
    checkTypeSyntax(typeName);
    // Create the type.
    final int typeCode = this.addType(typeName, ((TypeImpl) mother).getCode(), true);
    // If the type code is less than 1, it means that a type of that name
    // already exists.
    if (typeCode < this.typeNameST.getStart()) {
      return null;
    } // Get the created type.
    StringTypeImpl type = (StringTypeImpl) this.types.get(typeCode);
    type.setFeatureFinal();
    type.setInheritanceFinal();
    // Sort the String array.
    Arrays.sort(stringList);
    // Add the string array to the string sets.
    this.stringSets.add(stringList);
    return type;
  }

  Type getStringType() {
    return this.getType(CAS.TYPE_NAME_STRING);
  }

  public String[] getStringSet(int i) // public for ref from JCas TOP type,
  // impl FeatureStructureImpl
  {
    return (String[]) this.stringSets.get(i);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.uima.cas.TypeSystem#getTypeNameSpace(java.lang.String)
   */
  public TypeNameSpace getTypeNameSpace(String name) {
    if (!TypeSystemUtils.isTypeNameSpaceName(name)) {
      return null;
    }
    return new TypeNameSpaceImpl(name, this);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.uima.cas.impl.LowLevelTypeSystem#ll_getCodeForTypeName(java.lang.String)
   */
  public int ll_getCodeForTypeName(String typeName) {
    if (typeName == null) {
      throw new NullPointerException();
    }
    return this.typeNameST.get(typeName);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.uima.cas.impl.LowLevelTypeSystem#ll_getCodeForType(org.apache.uima.cas.Type)
   */
  public int ll_getCodeForType(Type type) {
    return ((TypeImpl) type).getCode();
  }

  public int ll_getCodeForFeatureName(String featureName) {
    if (featureName == null) {
      throw new NullPointerException();
    }
    if (!this.featureMap.containsKey(featureName)) {
      return UNKNOWN_FEATURE_CODE;
    }
    return this.featureMap.get(featureName);
  }

  public int ll_getCodeForFeature(Feature feature) {
    return ((FeatureImpl) feature).getCode();
  }

  public Type ll_getTypeForCode(int typeCode) {
    if (isType(typeCode)) {
      return (Type) this.types.get(typeCode);
    }
    return null;
  }

  public final int getLargestFeatureCode() {
    return this.getNumberOfFeatures();
  }

  public final boolean isFeature(int featureCode) {
    return ((featureCode > UNKNOWN_FEATURE_CODE) && (featureCode <= getLargestFeatureCode()));
  }

  public Feature ll_getFeatureForCode(int featureCode) {
    if (isFeature(featureCode)) {
      return (Feature) this.features.get(featureCode);
    }
    return null;
  }

  public int ll_getDomainType(int featureCode) {
    return intro(featureCode);
  }

  public int ll_getRangeType(int featureCode) {
    return range(featureCode);
  }

  public LowLevelTypeSystem getLowLevelTypeSystem() {
    return this;
  }

  public boolean ll_isStringSubtype(int type) {
    return this.stringSetMap.containsKey(type);
  }

  public boolean ll_isRefType(int typeCode) {
    final int typeClass = ll_getTypeClass(typeCode);
    switch (typeClass) {
      case LowLevelCAS.TYPE_CLASS_BOOLEAN:
      case LowLevelCAS.TYPE_CLASS_BYTE:
      case LowLevelCAS.TYPE_CLASS_SHORT:
      case LowLevelCAS.TYPE_CLASS_INT:
      case LowLevelCAS.TYPE_CLASS_FLOAT:
      case LowLevelCAS.TYPE_CLASS_LONG:
      case LowLevelCAS.TYPE_CLASS_DOUBLE:
      case LowLevelCAS.TYPE_CLASS_STRING: {
        return false;
      }
      default: {
        return true;
      }
    }
  }

  public Type getArrayType(Type componentType) {
    final int arrayTypeCode = ll_getArrayType(ll_getCodeForType(componentType));
    if (arrayTypeCode == UNKNOWN_TYPE_CODE) {
      return null;
    }
    return (Type) this.types.get(arrayTypeCode);
  }

  public final int ll_getTypeClass(int typeCode) {
    if (typeCode == this.booleanTypeCode) {
      return LowLevelCAS.TYPE_CLASS_BOOLEAN;
    }
    if (typeCode == this.byteTypeCode) {
      return LowLevelCAS.TYPE_CLASS_BYTE;
    }
    if (typeCode == this.shortTypeCode) {
      return LowLevelCAS.TYPE_CLASS_SHORT;
    }
    if (typeCode == this.intTypeCode) {
      return LowLevelCAS.TYPE_CLASS_INT;
    }
    if (typeCode == this.floatTypeCode) {
      return LowLevelCAS.TYPE_CLASS_FLOAT;
    }
    if (typeCode == this.longTypeCode) {
      return LowLevelCAS.TYPE_CLASS_LONG;
    }
    if (typeCode == this.doubleTypeCode) {
      return LowLevelCAS.TYPE_CLASS_DOUBLE;
    }
    if (this.ll_subsumes(this.stringTypeCode, typeCode)) {
      return LowLevelCAS.TYPE_CLASS_STRING;
    }
    if (typeCode == this.booleanArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_BOOLEANARRAY;
    }
    if (typeCode == this.byteArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_BYTEARRAY;
    }
    if (typeCode == this.shortArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_SHORTARRAY;
    }
    if (typeCode == this.intArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_INTARRAY;
    }
    if (typeCode == this.floatArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_FLOATARRAY;
    }
    if (typeCode == this.longArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_LONGARRAY;
    }
    if (typeCode == this.doubleArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_DOUBLEARRAY;
    }
    if (typeCode == this.stringArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_STRINGARRAY;
    }
    if (typeCode == this.fsArrayTypeCode) {
      return LowLevelCAS.TYPE_CLASS_FSARRAY;
    }
    return LowLevelCAS.TYPE_CLASS_FS;
  }

  public int ll_getArrayType(int componentTypeCode) {
    if (this.componentToArrayTypeMap.containsKey(componentTypeCode)) {
      return this.componentToArrayTypeMap.get(componentTypeCode);
    }
    return addArrayType(ll_getTypeForCode(componentTypeCode),
            ll_getTypeForCode(ll_computeArrayParentFromComponentType(componentTypeCode)));
  }

  int addArrayType(Type componentType, Type mother) {
    return ll_addArrayType(ll_getCodeForType(componentType), ll_getCodeForType(mother));
  }

  int ll_addArrayType(int componentTypeCode, int motherCode) {

    if (!ll_isValidTypeCode(componentTypeCode)) {
      return UNKNOWN_TYPE_CODE;
    }
    // The array type is new and needs to be created.
    String arrayTypeName = getArrayTypeName(ll_getTypeForCode(componentTypeCode).getName());
    int arrayTypeCode = this.typeNameST.set(arrayTypeName);
    this.componentToArrayTypeMap.put(componentTypeCode, arrayTypeCode);
    this.arrayToComponentTypeMap.put(arrayTypeCode, componentTypeCode);
    // Dummy call to keep the counts ok. Will never use these data
    // structures for array types.
    newType();
    TypeImpl arrayType = new TypeImpl(arrayTypeName, arrayTypeCode, this);
    this.types.add(arrayType);
    this.parents.add(motherCode);
    if (!isCommitted())
      this.numCommittedTypes = this.types.size();
    this.arrayCodeToTypeMap.put(arrayTypeCode, arrayType);
    // For built-in arrays, we need to add the abstract base array as parent
    // to the inheritance tree. This sucks. Assumptions about the base
    // array are all over the place. Would be nice to just remove it.
    // Add an edge to the tree.
    if (!isCommitted()) {
      final int arrayBaseType = ll_getCodeForTypeName(CAS.TYPE_NAME_ARRAY_BASE);
      ((IntVector) this.tree.get(arrayBaseType)).add(arrayTypeCode);
      // Update subsumption relation.
      updateSubsumption(arrayTypeCode, this.arrayBaseTypeCode);
    }
    return arrayTypeCode;
  }

  public boolean ll_isValidTypeCode(int typeCode) {
    return (this.typeNameST.getSymbol(typeCode) != null)
            || this.arrayToComponentTypeMap.containsKey(typeCode);
  }

  public boolean ll_isArrayType(int typeCode) {
    if (!ll_isValidTypeCode(typeCode)) {
      return false;
    }
    return this.arrayCodeToTypeMap.containsKey(typeCode);
  }

  public int ll_getComponentType(int arrayTypeCode) {
    if (ll_isArrayType(arrayTypeCode)) {
      return this.arrayToComponentTypeMap.get(arrayTypeCode);
    }
    return UNKNOWN_TYPE_CODE;
  }

  /* note that subtypes of String are considered primitive */
  public boolean ll_isPrimitiveType(int typeCode) {
    return !ll_isRefType(typeCode);
  }

  public String[] ll_getStringSet(int typeCode) {
    if (!ll_isValidTypeCode(typeCode)) {
      return null;
    }
    if (!ll_isStringSubtype(typeCode)) {
      return null;
    }
    return (String[]) this.stringSets.get(this.stringSetMap.get(typeCode));
  }

}
