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
import static androidx.room.compiler.codegen.compat.XConverters.toXPoet;
import static com.google.common.base.Preconditions.checkState;

import androidx.room.compiler.codegen.VisibilityModifier;
import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import com.squareup.kotlinpoet.KModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.Modifier;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XPropertySpec} helper methods. */
public final class XPropertySpecs {

  /**
   * Creates a {@code XPropertySpec} with the given {@code name}, {@code typeName}, and {@code
   * modifiers} and adds the given type-use nullability annotations to the type and the non-type-use
   * annotations to the parameter.
   */
  public static XPropertySpec of(
      String name, XTypeName typeName, Nullability nullability, Modifier... modifiers) {
    return builder(name, typeName, nullability, modifiers).build();
  }

  /**
   * Creates a {@code XPropertySpec} with the given {@code name}, {@code typeName}, and {@code
   * modifiers}.
   */
  public static XPropertySpec of(String name, XTypeName typeName, Modifier... modifiers) {
    return builder(name, typeName, modifiers).build();
  }

  /**
   * Creates a builder with the given {@code name}, {@code typeName}, and {@code modifiers} and adds
   * the given type-use nullability annotations to the type and the non-type-use annotations to the
   * parameter.
   */
  public static Builder builder(
      String name, XTypeName typeName, Nullability nullability, Modifier... modifiers) {
    return Builder.create(name, XTypeNames.withTypeNullability(typeName, nullability), modifiers)
        .addAnnotationNames(nullability.nonTypeUseNullableAnnotations());
  }

  /** Creates a builder with the given {@code name}, {@code typeName}, and {@code modifiers}. */
  public static Builder builder(String name, XTypeName typeName, Modifier... modifiers) {
    return Builder.create(name, typeName, modifiers);
  }

  /** Creates a builder with the given {@code name}, {@code typeName}, and {@code modifiers}. */
  public static Builder builder(TypeName typeName, String name, Modifier... modifiers) {
    return Builder.create(name, typeName, modifiers);
  }

  /** Builds an {@link XPropertySpec} in a way that is more similar to the JavaPoet API. */
  public static class Builder {
    private static Builder create(String name, Object typeName, Modifier... modifiers) {
      Builder builder = new Builder(name, typeName);
      builder.addModifiers(modifiers);
      return builder;
    }

    private final String name;
    private final Object typeName;
    private boolean isStatic = false;
    private boolean isMutable = true; // The default in JavaPoet is true, i.e. non-final.
    private VisibilityModifier visibility = null;
    private XCodeBlock initializer = null;
    private final List<XCodeBlock> javadocs = new ArrayList<>();
    private final List<Object> annotations = new ArrayList<>();

    private Builder(String name, Object typeName) {
      this.name = name;
      this.typeName = typeName;
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
      this.isStatic = isStatic;
      return this;
    }

    /** Sets the final/open modifier of the method. */
    @CanIgnoreReturnValue
    public Builder isMutable(boolean isMutable) {
      this.isMutable = isMutable;
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
          case STATIC:
            isStatic(true);
            break;
          case FINAL:
            isMutable(false);
            break;
          default:
            throw new AssertionError("Unexpected modifier: " + modifier);
        }
      }
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

    /** Adds the given statement to the method. */
    @CanIgnoreReturnValue
    public Builder initializer(String format, Object... args) {
      return initializer(XCodeBlock.of(format, args));
    }

    /** Adds the given statement to the method. */
    @CanIgnoreReturnValue
    public Builder initializer(XCodeBlock initializer) {
      checkState(this.initializer == null);
      this.initializer = initializer;
      return this;
    }

    /** Builds the method and returns an {@link XFunSpec}. */
    public XPropertySpec build() {
      // TODO(bcorso): XPoet currently doesn't have a way to set default visibility (e.g.
      // package-private in Java). In this case, we set it to private initially then manually remove
      // the private modifier later.
      VisibilityModifier initialVisibility =
          visibility == null ? VisibilityModifier.PRIVATE : visibility;

      XPropertySpec.Builder builder =
          XPropertySpec.builder(
              name,
              typeName instanceof XTypeName
                  ? (XTypeName) typeName
                  : toXPoet(
                      (TypeName) typeName,
                      // TODO(bcorso): Remove usages of TypeName. Typically, you might think we
                      // could just use toKTypeName() here but it can cause infinite recursion
                      // in some cases (See https://github.com/square/kotlinpoet/issues/2022)
                      // so for now we just use ANY_OBJECT as a placeholder.
                      toKotlinPoet(XTypeName.ANY_OBJECT)),
              initialVisibility,
              isMutable,
              /* addJavaNullabilityAnnotation= */ false);

      if (visibility == null) {
        // If the visibility wasn't set then we remove the temporary private visibility set above.
        toJavaPoet(builder).modifiers.remove(Modifier.PRIVATE);
        toKotlinPoet(builder).getModifiers().remove(KModifier.PRIVATE);
      }

      for (XCodeBlock javadoc : javadocs) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addJavadoc(toJavaPoet(javadoc));
      }

      if (initializer != null) {
        builder.initializer(initializer);
      }

      if (isStatic) {
        // TODO(bcorso): Handle the KotlinPoet side of this implementation.
        toJavaPoet(builder).addModifiers(Modifier.STATIC);
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

      return builder.build();
    }
  }

  private XPropertySpecs() {}
}
