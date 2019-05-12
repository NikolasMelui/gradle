/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.collections;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DynamicPropertyNamer;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;

public class DefaultDomainObjectCollectionFactory implements DomainObjectCollectionFactory {
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry servicesToInject;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    private final MutationGuard mutationGuard;

    public DefaultDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry servicesToInject, CollectionCallbackActionDecorator collectionCallbackActionDecorator, MutationGuard mutationGuard) {
        this.instantiatorFactory = instantiatorFactory;
        this.servicesToInject = servicesToInject;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.mutationGuard = mutationGuard;
    }

    @Override
    public <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType) {
        // TODO - this should also be using the decorating instantiator but cannot for backwards compatibility
        ReflectiveNamedDomainObjectFactory<T> objectFactory = new ReflectiveNamedDomainObjectFactory<T>(elementType, instantiatorFactory.injectLenient(servicesToInject));
        Instantiator instantiator = instantiatorFactory.decorateLenient();
        return instantiator.newInstance(FactoryNamedDomainObjectContainer.class, elementType, instantiator, new DynamicPropertyNamer(), objectFactory, mutationGuard, collectionCallbackActionDecorator);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType, NamedDomainObjectFactory<T> factory) {
        Instantiator instantiator = instantiatorFactory.decorateLenient();
        return instantiator.newInstance(FactoryNamedDomainObjectContainer.class, elementType, instantiator, new DynamicPropertyNamer(), factory, mutationGuard, collectionCallbackActionDecorator);
    }

    @Override
    public <T> DomainObjectSet<T> newDomainObjectSet(Class<T> elementType) {
        Instantiator instantiator = instantiatorFactory.decorateLenient();
        return instantiator.newInstance(DefaultDomainObjectSet.class, elementType, collectionCallbackActionDecorator);
    }
}
