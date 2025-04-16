/*
 * Copyright (C) 2025 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static androidx.room.compiler.codegen.compat.XConverters.toKotlinPoet;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.codegen.VisibilityModifier;
import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XName;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.kotlinpoet.KModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.Modifier;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XFunSpec} helper methods. */
public final class XFunSpecs {

  /** Returns a {@link Builder} that overrides the given method. */
  public static Builder overriding(XMethodElement method, XType owner) {
    Builder builder = overridingWithoutParameters(method, owner);
    XMethodType methodType = method.asMemberOf(owner);
    for (int i = 0; i < methodType.getParameterTypes().size(); i++) {
      XExecutableParameterElement parameter = method.getParameters().get(i);
      XType parameterType = methodType.getParameterTypes().get(i);
      builder.addParameter(JavaPoetExt.toParameterSpec(parameter, parameterType));
    }
    return builder;
  }

  /** Returns a {@link Builder} that overrides the given method without parameters. */
  public static Builder overridingWithoutParameters(XMethodElement method, XType owner) {
    XMethodType methodType = method.asMemberOf(owner);
    Nullability nullability = Nullability.of(method);
    TypeName returnType =
        Nullability.getTypeNameWithNullableAnnotations(methodType.getReturnType());

    Builder builder =
        // We're overriding the method so we have to use the jvm name here.
        methodBuilder(method.getJvmName())
            .addAnnotation(Override.class)
            .addAnnotationNames(nullability.nonTypeUseNullableAnnotations())
            .addTypeVariables(methodType.getTypeVariables())
            .varargs(method.isVarArgs())
            .returns(returnType);
    if (method.isPublic()) {
      builder.addModifiers(PUBLIC);
    } else if (method.isProtected()) {
      builder.addModifiers(PROTECTED);
    }
    return builder;
  }

  public static Builder methodBuilder(String name) {
    return new Builder(Builder.Kind.FUNCTION).name(name);
  }

  public static Builder constructorBuilder() {
    return new Builder(Builder.Kind.CONSTRUCTOR);
  }

  /** Builds an {@link XFunSpec} in a way that is more similar to the JavaPoet API. */
  public static class Builder {
    private static enum Kind {
      FUNCTION,
      CONSTRUCTOR
    }

    private final Kind kind;
    private final XCodeBlock.Builder bodyBuilder = XCodeBlock.builder();
    private String name;
    private VisibilityModifier visibility = null;
    private boolean isStatic = false;
    private boolean isAbstract = false;
    private boolean isOpen = false;
    private boolean isOverride = false;
    private boolean isVarArgs = false;
    private final List<XCodeBlock> javadocs = new ArrayList<>();
    // For now, we use a Object to allow for both XPoet and JavaPoet types.
    private Object returnType = null;
    private final List<Object> annotations = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();
    private final List<Object> typeVariableNames = new ArrayList<>();
    private final List<Object> exceptionNames = new ArrayList<>();

    Builder(Kind kind) {
      this.kind = kind;
    }

    @CanIgnoreReturnValue
    private Builder name(String name) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.name = name;
      return this;
    }

    /** Sets the visibility of the method. */
    @CanIgnoreReturnValue
    public Builder visibility(VisibilityModifier visibility) {
      this.visibility = visibility;
      return this;
    }

    /** Sets the static modifier of the method. */
    @CanIgnoreReturnValue
    public Builder isStatic(boolean isStatic) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.isStatic = isStatic;
      return this;
    }

    /** Sets the abstract modifier of the method. */
    @CanIgnoreReturnValue
    public Builder isAbstract(boolean isAbstract) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.isAbstract = isAbstract;
      return this;
    }

    /** Sets the final/open modifier of the method. */
    @CanIgnoreReturnValue
    public Builder isOpen(boolean isOpen) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.isOpen = isOpen;
      return this;
    }

    /** Sets whether the method is an override. */
    @CanIgnoreReturnValue
    public Builder isOverride(boolean isOverride) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.isOverride = isOverride;
      return this;
    }

    /** Sets the originating element of the type. */
    @CanIgnoreReturnValue
    public Builder addJavadoc(String format, Object... args) {
      javadocs.add(XCodeBlock.of(format, args));
      return this;
    }

    /**
     * Sets the modifiers of the method.
     *
     * @deprecated Use the individual setter methods instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addModifiers(Collection<Modifier> modifiers) {
      return addModifiers(modifiers.toArray(new Modifier[0]));
    }

    /**
     * Sets the modifiers of the method.
     *
     * @deprecated Use the individual setter methods instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addModifiers(Modifier... modifiers) {
      for (Modifier modifier : modifiers) {
        switch (modifier) {
          case PUBLIC:
            visibility(VisibilityModifier.PUBLIC);
            break;
          case PRIVATE:
            visibility(VisibilityModifier.PRIVATE);
            break;
          case PROTECTED:
            visibility(VisibilityModifier.PROTECTED);
            break;
          case ABSTRACT:
            isOpen(true);
            isAbstract(true);
            break;
          case STATIC:
            isStatic(true);
            break;
          case FINAL:
            isOpen(false);
            break;
          default:
            throw new AssertionError("Unexpected modifier: " + modifier);
        }
      }
      return this;
    }

    /** Adds the given type variables to the method. */
    @CanIgnoreReturnValue
    public Builder addTypeVariables(Collection<? extends XType> typeVariables) {
      typeVariables.forEach(this::addTypeVariable);
      return this;
    }

    /** Adds the given type variable names to the method. */
    @CanIgnoreReturnValue
    public Builder addTypeVariableNames(Collection<XTypeName> typeVariableNames) {
      typeVariableNames.forEach(this::addTypeVariable);
      return this;
    }

    /**
     * Adds the given type variable names to the method.
     *
     * @deprecated Use {@link #addTypeVariableNames(Collection<XTypeName>)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addJavaTypeVariableNames(Collection<TypeVariableName> typeVariableNames) {
      typeVariableNames.forEach(this::addTypeVariable);
      return this;
    }

    /** Adds the given type variable to the method. */
    @CanIgnoreReturnValue
    public Builder addTypeVariable(XType type) {
      return addTypeVariable(type.asTypeName());
    }

    /** Adds the given type variable name to the method. */
    @CanIgnoreReturnValue
    public Builder addTypeVariable(XTypeName typeName) {
      typeVariableNames.add(typeName);
      return this;
    }

    /**
     * Adds the given type variable name to the method.
     *
     * @deprecated Use {@link #addTypeVariable(XTypeName)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addTypeVariable(TypeVariableName typeVariableName) {
      typeVariableNames.add(typeVariableName);
      return this;
    }

    /** Adds the given annotations to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotations(Collection<XAnnotationSpec> annotations) {
      annotations.forEach(this::addAnnotation);
      return this;
    }

    /**
     * Adds the given annotations to the method.
     *
     * @deprecated Use {@link #addAnnotation(XAnnotationSpec)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addJavaAnnotations(Collection<AnnotationSpec> annotations) {
      annotations.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation names to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotationNames(Collection<XClassName> annotationNames) {
      annotationNames.forEach(this::addAnnotation);
      return this;
    }

    /** Adds the given annotation to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XAnnotationSpec annotation) {
      annotations.add(annotation);
      return this;
    }

    /** Adds the given annotation name to the method. */
    @CanIgnoreReturnValue
    public Builder addAnnotation(XClassName annotationName) {
      return addAnnotation(XAnnotationSpec.of(annotationName));
    }

    /**
     * Adds the given annotation to the method.
     *
     * @deprecated Use {@link #addAnnotation(XClassName)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addAnnotation(Class<?> clazz) {
      if (clazz.equals(Override.class)) {
        isOverride(true);
      }
      addAnnotation(AnnotationSpec.builder(ClassName.get(clazz)).build());
      return this;
    }

    /**
     * Adds the given annotation to the method.
     *
     * @deprecated Use {@link #addAnnotation(XAnnotationSpec)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addAnnotation(AnnotationSpec annotation) {
      annotations.add(annotation);
      return this;
    }

    /** Adds the given parameters to the method. */
    @CanIgnoreReturnValue
    public Builder addParameters(Collection<XParameterSpec> parameters) {
      parameters.forEach(this::addParameter);
      return this;
    }

    /**
     * Adds the given parameters to the method.
     *
     * @deprecated Use {@link #addParameter(XParameterSpec)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addJavaParameters(Collection<ParameterSpec> parameters) {
      parameters.forEach(this::addParameter);
      return this;
    }

    /** Adds the given parameter to the method. */
    @CanIgnoreReturnValue
    public Builder addParameter(XParameterSpec parameter) {
      parameters.add(parameter);
      return this;
    }

    /** Adds the given parameter to the method. */
    @CanIgnoreReturnValue
    public Builder addParameter(XName name, XTypeName typeName) {
      return addParameter(XParameterSpecs.of(toJavaPoet(name), typeName));
    }

    /** Adds the given parameter to the method. */
    @CanIgnoreReturnValue
    public Builder addParameter(String name, XTypeName typeName) {
      return addParameter(XParameterSpecs.of(name, typeName));
    }

    /**
     * Adds the given parameter to the method.
     *
     * @deprecated Use {@link #addParameter(XParameterSpec)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addParameter(ParameterSpec parameter) {
      parameters.add(parameter);
      return this;
    }

    /**
     * Adds the given parameter to the method.
     *
     * @deprecated Use {@link #addParameter(XParameterSpec)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addParameter(TypeName typeName, String name) {
      parameters.add(ParameterSpec.builder(typeName, name).build());
      return this;
    }

    /** Adds the given exceptions to the method. */
    @CanIgnoreReturnValue
    public Builder addExceptions(Collection<? extends XType> exceptions) {
      exceptions.forEach(this::addException);
      return this;
    }

    /** Adds the given exception names to the method. */
    @CanIgnoreReturnValue
    public Builder addExceptionNames(Collection<XTypeName> exceptionNames) {
      exceptionNames.forEach(this::addException);
      return this;
    }

    /**
     * Adds the given exception names to the method.
     *
     * @deprecated Use {@link #addExceptionNames(Collection<XTypeName>)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addJavaExceptionNames(Collection<TypeName> exceptionNames) {
      exceptionNames.forEach(this::addException);
      return this;
    }

    /** Adds the given exception to the method. */
    @CanIgnoreReturnValue
    public Builder addException(XType exception) {
      return addException(exception.asTypeName());
    }

    /** Adds the given exception name to the method. */
    @CanIgnoreReturnValue
    public Builder addException(XTypeName exceptionName) {
      exceptionNames.add(exceptionName);
      return this;
    }

    /**
     * Adds the given exception name to the method.
     *
     * @deprecated Use {@link #addException(XTypeName)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder addException(TypeName exceptionName) {
      exceptionNames.add(exceptionName);
      return this;
    }

    /** Adds the given statement to the method. */
    @CanIgnoreReturnValue
    public Builder addStatement(String format, Object... args) {
      bodyBuilder.addStatement(format, args);
      return this;
    }

    /** Adds the given statement to the method. */
    @CanIgnoreReturnValue
    public Builder addStatement(XCodeBlock codeBlock) {
      bodyBuilder.addStatement("%L", codeBlock);
      return this;
    }

    /** Adds the given code to the method. */
    @CanIgnoreReturnValue
    public Builder addCode(String format, Object... args) {
      bodyBuilder.add(format, args);
      return this;
    }

    /** Adds the given code to the method. */
    @CanIgnoreReturnValue
    public Builder addCode(XCodeBlock codeBlock) {
      bodyBuilder.add(codeBlock);
      return this;
    }

    /** Begins a control flow block. */
    @CanIgnoreReturnValue
    public Builder beginControlFlow(String controlFlow, Object... args) {
      bodyBuilder.beginControlFlow(controlFlow, args);
      return this;
    }

    /** Ends a control flow block. */
    @CanIgnoreReturnValue
    public Builder endControlFlow() {
      bodyBuilder.endControlFlow();
      return this;
    }

    /** Sets whether the method is varargs. */
    @CanIgnoreReturnValue
    public Builder varargs(boolean isVarArgs) {
      this.isVarArgs = isVarArgs;
      return this;
    }

    /** Sets the return type of the method. */
    @CanIgnoreReturnValue
    public Builder returns(XTypeName returnType) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.returnType = returnType;
      return this;
    }

    /**
     * Sets the return type of the method.
     *
     * @deprecated Use {@link #returns(XTypeName)} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder returns(TypeName returnType) {
      checkState(kind != Kind.CONSTRUCTOR);
      this.returnType = returnType;
      return this;
    }

    /** Builds the method and returns an {@link XFunSpec}. */
    public XFunSpec build() {
      // TODO(bcorso): XPoet currently doesn't have a way to set default visibility (e.g.
      // package-private in Java). In this case, we set it to private initially then maually remove
      // the private modifier later.
      VisibilityModifier initialVisibility =
          visibility == null ? VisibilityModifier.PRIVATE : visibility;

      XFunSpec.Builder builder;
      switch (kind) {
        case FUNCTION:
          builder =
              XFunSpec.builder(
                  name,
                  initialVisibility,
                  isOpen,
                  isOverride,
                  /* addJavaNullabilityAnnotation= */ false);
          // If the override annotation exists, remove it from the builder and it will be added
          // with the other annotations to avoid unnecessary diffs during the migration.
          AnnotationSpec overrideAnnotation = AnnotationSpec.builder(Override.class).build();
          if (annotations.contains(overrideAnnotation)) {
            toJavaPoet(builder).annotations.remove(overrideAnnotation);
          }
          break;
        case CONSTRUCTOR:
          checkState(name == null);
          checkState(returnType == null);
          builder =
              XFunSpec.constructorBuilder(
                  initialVisibility, /* addJavaNullabilityAnnotation= */ false);
          break;
        default:
          throw new AssertionError();
      }

      if (visibility == null) {
        // If the visibility wasn't set then we remove the temporary private visibility set above.
        toJavaPoet(builder).modifiers.remove(Modifier.PRIVATE);
        toKotlinPoet(builder).getModifiers().remove(KModifier.PRIVATE);
      }

      for (XCodeBlock javadoc : javadocs) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addJavadoc(toJavaPoet(javadoc));
      }

      XCodeBlock body = bodyBuilder.build();
      if (!XCodeBlocks.isEmpty(body)) {
        builder.addCode(body);
      }

      if (isAbstract) {
        builder.addAbstractModifier();
      }

      if (isStatic) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addModifiers(Modifier.STATIC);
      }

      if (isVarArgs) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).varargs(isVarArgs);
      }

      if (returnType != null) {
        if (returnType instanceof XTypeName) {
          builder.returns((XTypeName) returnType);
        } else if (returnType instanceof TypeName) {
          toJavaPoet(builder).returns((TypeName) returnType);
        } else {
          throw new AssertionError("Unexpected returnType class: " + returnType.getClass());
        }
      }

      for (Object parameter : parameters) {
        if (parameter instanceof XParameterSpec) {
          builder.addParameter((XParameterSpec) parameter);
        } else if (parameter instanceof ParameterSpec) {
          toJavaPoet(builder).addParameter((ParameterSpec) parameter);
        } else {
          throw new AssertionError("Unexpected parameter class: " + parameter.getClass());
        }
      }

      for (Object typeVariableName : typeVariableNames) {
        if (typeVariableName instanceof XTypeName) {
          builder.addTypeVariable((XTypeName) typeVariableName);
        } else if (typeVariableName instanceof TypeVariableName) {
          toJavaPoet(builder).addTypeVariable((TypeVariableName) typeVariableName);
        } else {
          throw new AssertionError(
              "Unexpected typeVariableName class: " + typeVariableName.getClass());
        }
      }

      for (Object annotation : annotations) {
        if (annotation instanceof XAnnotationSpec) {
          builder.addAnnotation((XAnnotationSpec) annotation);
        } else if (annotation instanceof AnnotationSpec) {
          toJavaPoet(builder).addAnnotation((AnnotationSpec) annotation);
        } else {
          throw new AssertionError("Unexpected annotation class: " + annotation.getClass());
        }
      }

      for (Object exceptionName : exceptionNames) {
        if (exceptionName instanceof XTypeName) {
          // TODO(bcorso): Handle the KotlinPoet side of this implementation.
          toJavaPoet(builder).addException(toJavaPoet((XTypeName) exceptionName));
        } else if (exceptionName instanceof TypeName) {
          toJavaPoet(builder).addException((TypeName) exceptionName);
        } else {
          throw new AssertionError("Unexpected exceptionName class: " + exceptionName.getClass());
        }
      }

      return builder.build();
    }
  }

  private XFunSpecs() {}
}
