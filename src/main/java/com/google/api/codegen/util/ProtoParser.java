/* Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.util;

import static com.google.api.FieldBehavior.REQUIRED;

import com.google.api.AnnotationsProto;
import com.google.api.MethodSignature;
import com.google.api.OperationData;
import com.google.api.Resource;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Api;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

// Utils for parsing possibly-annotated protobuf API IDL.
public class ProtoParser {

  /** Return the path, e.g. "shelves/*" for a resource field. Return null if no path found. */
  public String getResourcePath(Field element) {
    Resource resource =
        (Resource) element.getOptionFields().get(AnnotationsProto.resource.getDescriptor());
    if (resource != null) {
      return resource.getPath();
    }
    return null;
  }

  /** Returns a base package name for an API's client. */
  @Nullable
  public String getPackageName(Model model) {
    if (model.getServiceConfig().getApisCount() > 0) {
      Api api = model.getServiceConfig().getApis(0);
      Interface apiInterface = model.getSymbolTable().lookupInterface(api.getName());
      if (apiInterface != null) {
        return apiInterface.getFile().getFullName();
      }
    }
    return null;
  }

  /** Return the entity name, e.g. "shelf" for a resource field. */
  public String getResourceEntityName(Field field) {
    return field.getParent().getSimpleName().toLowerCase();
  }

  /** Get long running settings. */
  public OperationData getLongRunningOperation(Method method) {
    return method.getDescriptor().getMethodAnnotation(AnnotationsProto.operation);
  }

  @SuppressWarnings("unchecked")
  /* Return a list of method signatures, aka flattenings, specified on a given method.
   * This flattens the repeated additionalSignatures into the returned list of MethodSignatures. */
  public List<MethodSignature> getMethodSignatures(Method method) {
    List<MethodSignature> methodSignatures =
        (List<MethodSignature>)
            method.getOptionFields().get(AnnotationsProto.methodSignature.getDescriptor());
    if (methodSignatures == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(methodSignatures);
  }

  /** Return the names of required parameters of a method. */
  public List<String> getRequiredFields(Method method) {
    MessageType inputMessage = method.getInputMessage();
    return inputMessage
        .getFields()
        .stream()
        .filter(this::isFieldRequired)
        .map(Field::getSimpleName)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  /* Returns if a field is required, according to the proto annotations. */
  private boolean isFieldRequired(Field field) {
    List<EnumValueDescriptor> fieldBehaviors =
        (List<EnumValueDescriptor>)
            field.getOptionFields().get(AnnotationsProto.fieldBehavior.getDescriptor());
    return fieldBehaviors != null && fieldBehaviors.contains(REQUIRED.getValueDescriptor());
  }

  /** Return the resource type for the given field. */
  public String getResourceType(Field field) {
    return (String) field.getOptionFields().get(AnnotationsProto.resourceReference.getDescriptor());
  }

  /** Return whether the method has the HttpRule for GET. */
  public boolean isHttpGetMethod(Method method) {
    return !Strings.isNullOrEmpty(
        method.getDescriptor().getMethodAnnotation(AnnotationsProto.http).getGet());
  }

  /** The hostname for this service (e.g. "foo.googleapis.com"). */
  public String getServiceAddress(Interface service) {
    return service.getProto().getOptions().getExtension(AnnotationsProto.defaultHost);
  }

  /** The OAuth scopes for this service (e.g. "https://cloud.google.com/auth/cloud-platform"). */
  public List<String> getAuthScopes(Interface service) {
    return service.getProto().getOptions().getExtension(AnnotationsProto.oauth).getScopesList();
  }
}
