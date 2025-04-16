/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethod;
import static dagger.internal.codegen.writing.AssistedInjectionParameters.assistedFactoryParameterSpecs;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XFunSpecs.overridingWithoutParameters;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.AssistedFactoryBinding;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import java.util.Optional;

/**
 * A {@link dagger.internal.codegen.writing.RequestRepresentation} for {@link
 * dagger.assisted.AssistedFactory} methods.
 */
final class AssistedFactoryRequestRepresentation extends RequestRepresentation {
  private final AssistedFactoryBinding binding;
  private final BindingGraph graph;
  private final SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory;
  private final ComponentImplementation componentImplementation;

  @AssistedInject
  AssistedFactoryRequestRepresentation(
      @Assisted AssistedFactoryBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory) {
    this.binding = checkNotNull(binding);
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.simpleMethodRequestRepresentationFactory = simpleMethodRequestRepresentationFactory;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    // Get corresponding assisted injection binding.
    Optional<Binding> localBinding = graph.localContributionBinding(binding.assistedInjectKey());
    checkArgument(
        localBinding.isPresent(),
        "assisted factory should have a dependency on an assisted injection binding");
    XExpression assistedInjectionExpression =
        simpleMethodRequestRepresentationFactory
            .create((AssistedInjectionBinding) localBinding.get())
            .getDependencyExpression(requestingClass.peerClass(""));
    return XExpression.create(
        assistedInjectionExpression.type(),
        XCodeBlock.of("%L", anonymousfactoryImpl(localBinding.get(), assistedInjectionExpression)));
  }

  private XTypeSpec anonymousfactoryImpl(
      Binding assistedBinding, XExpression assistedInjectionExpression) {
    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    XType factoryType = binding.key().type().xprocessing();
    XMethodElement factoryMethod = assistedFactoryMethod(factory);

    XTypeSpecs.Builder builder =
        XTypeSpecs.anonymousClassBuilder()
            .addFunction(
                overridingWithoutParameters(factoryMethod, factoryType)
                    .addParameters(
                        assistedFactoryParameterSpecs(
                            binding, componentImplementation.shardImplementation(assistedBinding)))
                    .addStatement("return %L", assistedInjectionExpression.codeBlock())
                    .build());

    if (factory.isInterface()) {
      builder.addSuperinterface(factoryType.getTypeName());
    } else {
      builder.superclass(factoryType.getTypeName());
    }

    return builder.build();
  }

  @AssistedFactory
  static interface Factory {
    AssistedFactoryRequestRepresentation create(AssistedFactoryBinding binding);
  }
}
