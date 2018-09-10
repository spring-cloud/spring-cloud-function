/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.core.FunctionFactoryMetadata;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.core.type.classreading.MethodMetadataReadingVisitor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Oleg Zhurakousky
 *
 * @since 2.0
 */
abstract class FunctionContextUtils {

    public static FunctionType findType(String name, ConfigurableListableBeanFactory registry) {
        AbstractBeanDefinition definition = (AbstractBeanDefinition) registry.getBeanDefinition(name);
        Object source = definition.getSource();
        FunctionType param = null;
        // Start by assuming output -> Function
        if (source instanceof StandardMethodMetadata) {
            // Standard @Bean metadata
            Type beanType = ((StandardMethodMetadata) source).getIntrospectedMethod()
                    .getGenericReturnType();
            if (beanType instanceof ParameterizedType) {
                ParameterizedType type = (ParameterizedType) beanType;
                param = new FunctionType(type);
            }
            else {
                param = new FunctionType(beanType);
            }
        }
        else if (source instanceof MethodMetadataReadingVisitor) {
            // A component scan with @Beans
            MethodMetadataReadingVisitor visitor = (MethodMetadataReadingVisitor) source;
            Type type = findBeanType(definition, visitor);
            param = new FunctionType(type);
        }
        else if (source instanceof Resource) {
            Class<?> beanType = registry.getType(name);
            param = new FunctionType(beanType);
        }
        else {
            ResolvableType resolvable = (ResolvableType) getField(definition,
                    "targetType");
            if (resolvable != null) {
                param = new FunctionType(resolvable.getType());
            }
            else {
                Class<?> beanClass = definition.getBeanClass();
                if (beanClass != null && !FunctionFactoryMetadata.class
                        .isAssignableFrom(beanClass)) {
                    Type type = beanClass;
                    param = new FunctionType(type);
                }
                else {
                    Object bean = registry.getBean(name);
                    if (bean instanceof FunctionFactoryMetadata) {
                        FunctionFactoryMetadata<?> factory = (FunctionFactoryMetadata<?>) bean;
                        Type type = factory.getFactoryMethod().getGenericReturnType();
                        param = new FunctionType(type);
                    }
                    else {
                        param = new FunctionType(bean.getClass());
                    }
                }
            }
        }
        return param;
    }

    private static Type findBeanType(AbstractBeanDefinition definition,
                              MethodMetadataReadingVisitor visitor) {
        Class<?> factory = ClassUtils
                .resolveClassName(visitor.getDeclaringClassName(), null);
        Class<?>[] params = getParamTypes(factory, definition);
        Method method = ReflectionUtils.findMethod(factory, visitor.getMethodName(),
                params);
        Type type = method.getGenericReturnType();
        return type;
    }

    private static Class<?>[] getParamTypes(Class<?> factory,
                                     AbstractBeanDefinition definition) {
        if (definition instanceof RootBeanDefinition) {
            RootBeanDefinition root = (RootBeanDefinition) definition;
            for (Method method : getCandidateMethods(factory, root)) {
                if (root.isFactoryMethod(method)) {
                    return method.getParameterTypes();
                }
            }
        }
        List<Class<?>> params = new ArrayList<>();
        for (ConstructorArgumentValues.ValueHolder holder : definition.getConstructorArgumentValues()
                .getIndexedArgumentValues().values()) {
            params.add(ClassUtils.resolveClassName(holder.getType(), null));
        }
        return params.toArray(new Class<?>[0]);
    }

    private static Method[] getCandidateMethods(final Class<?> factoryClass,
                                         final RootBeanDefinition mbd) {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(new PrivilegedAction<Method[]>() {
                @Override
                public Method[] run() {
                    return (mbd.isNonPublicAccessAllowed()
                            ? ReflectionUtils.getAllDeclaredMethods(factoryClass)
                            : factoryClass.getMethods());
                }
            });
        }
        else {
            return (mbd.isNonPublicAccessAllowed()
                    ? ReflectionUtils.getAllDeclaredMethods(factoryClass)
                    : factoryClass.getMethods());
        }
    }

    private static Object getField(Object target, String name) {
        Field field = ReflectionUtils.findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }
}
