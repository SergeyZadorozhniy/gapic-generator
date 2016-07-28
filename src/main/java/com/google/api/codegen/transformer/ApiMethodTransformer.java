/* Copyright 2016 Google Inc
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
package com.google.api.codegen.transformer;

import com.google.api.codegen.MethodConfig;
import com.google.api.codegen.ServiceMessages;
import com.google.api.codegen.util.Name;
import com.google.api.codegen.viewmodel.ApiMethodDocView;
import com.google.api.codegen.viewmodel.ApiMethodType;
import com.google.api.codegen.viewmodel.DynamicDefaultableParamView;
import com.google.api.codegen.viewmodel.MapParamDocView;
import com.google.api.codegen.viewmodel.OptionalArrayMethodView;
import com.google.api.codegen.viewmodel.ParamDocView;
import com.google.api.codegen.viewmodel.RequestObjectParamView;
import com.google.api.codegen.viewmodel.SimpleParamDocView;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.TypeRef;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ApiMethodTransformer generates view objects from method definitions.
 */
public class ApiMethodTransformer {
  private InitCodeTransformer initCodeTransformer;

  public ApiMethodTransformer() {
    this.initCodeTransformer = new InitCodeTransformer();
  }

  public OptionalArrayMethodView generateOptionalArrayMethod(MethodTransformerContext context) {
    SurfaceNamer namer = context.getNamer();
    OptionalArrayMethodView.Builder apiMethod = OptionalArrayMethodView.newBuilder();

    if (context.getMethodConfig().isPageStreaming()) {
      apiMethod.type(ApiMethodType.PagedOptionalArrayMethod);
    } else {
      apiMethod.type(ApiMethodType.OptionalArrayMethod);
    }
    apiMethod.apiClassName(namer.getApiWrapperClassName(context.getInterface()));
    apiMethod.apiVariableName(namer.getApiWrapperVariableName(context.getInterface()));
    apiMethod.initCode(
        initCodeTransformer.generateInitCode(
            context, context.getMethodConfig().getRequiredFields()));

    apiMethod.doc(generateOptionalArrayMethodDoc(context));

    apiMethod.name(namer.getApiMethodName(context.getMethod()));
    apiMethod.requestTypeName(
        context.getTypeTable().getAndSaveNicknameFor(context.getMethod().getInputType()));
    apiMethod.hasReturnValue(!ServiceMessages.s_isEmptyType(context.getMethod().getOutputType()));
    apiMethod.key(namer.getMethodKey(context.getMethod()));
    apiMethod.grpcMethodName(namer.getGrpcMethodName(context.getMethod()));

    apiMethod.methodParams(generateOptionalArrayMethodParams(context));

    apiMethod.requiredRequestObjectParams(
        generateRequestObjectParams(context, context.getMethodConfig().getRequiredFields()));
    apiMethod.optionalRequestObjectParams(
        generateRequestObjectParams(context, context.getMethodConfig().getOptionalFields()));

    return apiMethod.build();
  }

  private ApiMethodDocView generateOptionalArrayMethodDoc(MethodTransformerContext context) {
    ApiMethodDocView.Builder docBuilder = ApiMethodDocView.newBuilder();

    docBuilder.mainDocLines(context.getNamer().getDocLines(context.getMethod()));
    List<ParamDocView> paramDocs =
        getMethodParamDocs(context, context.getMethodConfig().getRequiredFields());
    paramDocs.add(getOptionalArrayParamDoc(context, context.getMethodConfig().getOptionalFields()));
    paramDocs.add(getCallSettingsParamDoc(context));
    docBuilder.paramDocs(paramDocs);
    docBuilder.returnTypeName(
        context
            .getNamer()
            .getDynamicReturnTypeName(context.getMethod(), context.getMethodConfig()));
    docBuilder.throwsDocLines(new ArrayList<String>());

    return docBuilder.build();
  }

  private List<DynamicDefaultableParamView> generateOptionalArrayMethodParams(
      MethodTransformerContext context) {
    List<DynamicDefaultableParamView> methodParams =
        generateDefaultableParams(context, context.getMethodConfig().getRequiredFields());

    // TODO create a map TypeRef here instead of an array
    // (not done yet because array is sufficient for PHP, and maps are more complex to construct)
    TypeRef arrayType = TypeRef.fromPrimitiveName("string").makeRepeated();

    DynamicDefaultableParamView.Builder optionalArgs = DynamicDefaultableParamView.newBuilder();
    optionalArgs.name(context.getNamer().varName(Name.from("optional", "args")));
    optionalArgs.defaultValue(context.getTypeTable().getZeroValueAndSaveNicknameFor(arrayType));
    methodParams.add(optionalArgs.build());

    DynamicDefaultableParamView.Builder callSettings = DynamicDefaultableParamView.newBuilder();
    callSettings.name(context.getNamer().varName(Name.from("call", "settings")));
    callSettings.defaultValue(context.getTypeTable().getZeroValueAndSaveNicknameFor(arrayType));
    methodParams.add(callSettings.build());

    return methodParams;
  }

  private List<DynamicDefaultableParamView> generateDefaultableParams(
      MethodTransformerContext context, Iterable<Field> fields) {
    List<DynamicDefaultableParamView> methodParams = new ArrayList<>();
    for (Field field : context.getMethodConfig().getRequiredFields()) {
      DynamicDefaultableParamView param =
          DynamicDefaultableParamView.newBuilder()
              .name(context.getNamer().getVariableName(field))
              .defaultValue("")
              .build();
      methodParams.add(param);
    }
    return methodParams;
  }

  private List<RequestObjectParamView> generateRequestObjectParams(
      MethodTransformerContext context, Iterable<Field> fields) {
    List<RequestObjectParamView> params = new ArrayList<>();
    for (Field field : fields) {
      params.add(generateRequestObjectParam(context, field));
    }
    return params;
  }

  private RequestObjectParamView generateRequestObjectParam(
      MethodTransformerContext context, Field field) {
    SurfaceNamer namer = context.getNamer();
    RequestObjectParamView.Builder param = RequestObjectParamView.newBuilder();
    param.name(namer.getVariableName(field));
    if (namer.shouldImportRequestObjectParamType(field)) {
      param.elementTypeName(
          context.getTypeTable().getAndSaveNicknameForElementType(field.getType()));
      param.typeName(context.getTypeTable().getAndSaveNicknameFor(field.getType()));
    } else {
      param.elementTypeName(
          namer.getNotImplementedString(
              "ApiMethodTransformer.generateRequestObjectParam - elementTypeName"));
      param.typeName(
          namer.getNotImplementedString(
              "ApiMethodTransformer.generateRequestObjectParam - typeName"));
    }
    param.setCallName(
        namer.getSetFunctionCallName(field.getType(), Name.from(field.getSimpleName())));
    param.isMap(field.getType().isMap());
    param.isArray(!field.getType().isMap() && field.getType().isRepeated());
    return param.build();
  }

  private List<ParamDocView> getMethodParamDocs(
      MethodTransformerContext context, Iterable<Field> fields) {
    List<ParamDocView> allDocs = new ArrayList<>();
    for (Field field : fields) {
      SimpleParamDocView.Builder paramDoc = SimpleParamDocView.newBuilder();
      paramDoc.paramName(context.getNamer().getVariableName(field));
      paramDoc.typeName(context.getTypeTable().getAndSaveNicknameFor(field.getType()));

      List<String> docLines = null;
      MethodConfig methodConfig = context.getMethodConfig();
      if (methodConfig.isPageStreaming()
          && methodConfig.getPageStreaming().hasPageSizeField()
          && field.equals(methodConfig.getPageStreaming().getPageSizeField())) {
        docLines =
            Arrays.asList(
                new String[] {
                  "The maximum number of resources contained in the underlying API",
                  "response. If page streaming is performed per-resource, this",
                  "parameter does not affect the return value. If page streaming is",
                  "performed per-page, this determines the maximum number of",
                  "resources in a page."
                });
      } else {
        docLines = context.getNamer().getDocLines(field);
      }

      paramDoc.firstLine(docLines.get(0));
      paramDoc.remainingLines(docLines.subList(1, docLines.size()));

      allDocs.add(paramDoc.build());
    }
    return allDocs;
  }

  private ParamDocView getOptionalArrayParamDoc(
      MethodTransformerContext context, Iterable<Field> fields) {
    MapParamDocView.Builder paramDoc = MapParamDocView.newBuilder();

    Name optionalArgsName = Name.from("optional", "args");

    paramDoc.paramName(context.getNamer().varName(optionalArgsName));
    paramDoc.typeName(context.getNamer().getOptionalArrayTypeName());

    List<String> docLines = null;
    if (!fields.iterator().hasNext()) {
      // TODO figure out a reliable way to line-wrap comments across all languages
      // instead of encoding it in the transformer
      String retrySettingsDocText =
          String.format(
              "Optional. There are no optional parameters for this method yet;\n"
                  + "          this %s parameter reserves a spot for future ones.",
              context.getNamer().varReference(optionalArgsName));
      docLines = context.getNamer().getDocLines(retrySettingsDocText);
    } else {
      docLines = Arrays.asList("Optional.");
    }
    paramDoc.firstLine(docLines.get(0));
    paramDoc.remainingLines(docLines.subList(1, docLines.size()));

    paramDoc.arrayKeyDocs(getMethodParamDocs(context, fields));

    return paramDoc.build();
  }

  private ParamDocView getCallSettingsParamDoc(MethodTransformerContext context) {
    MapParamDocView.Builder paramDoc = MapParamDocView.newBuilder();

    paramDoc.paramName(context.getNamer().varName(Name.from("call", "settings")));
    paramDoc.typeName(context.getNamer().getOptionalArrayTypeName());
    paramDoc.firstLine("Optional.");
    paramDoc.remainingLines(new ArrayList<String>());

    List<ParamDocView> arrayKeyDocs = new ArrayList<>();
    SimpleParamDocView.Builder retrySettingsDoc = SimpleParamDocView.newBuilder();
    retrySettingsDoc.typeName(context.getNamer().getRetrySettingsTypeName());

    Name retrySettingsName = Name.from("retry", "settings");
    Name timeoutMillisName = Name.from("timeout", "millis");

    retrySettingsDoc.paramName(context.getNamer().varName(retrySettingsName));
    // TODO figure out a reliable way to line-wrap comments across all languages
    // instead of encoding it in the transformer
    String retrySettingsDocText =
        String.format(
            "Retry settings to use for this call. If present, then\n%s is ignored.",
            context.getNamer().varReference(timeoutMillisName));
    List<String> retrySettingsDocLines = context.getNamer().getDocLines(retrySettingsDocText);
    retrySettingsDoc.firstLine(retrySettingsDocLines.get(0));
    retrySettingsDoc.remainingLines(retrySettingsDocLines.subList(1, retrySettingsDocLines.size()));
    arrayKeyDocs.add(retrySettingsDoc.build());

    SimpleParamDocView.Builder timeoutDoc = SimpleParamDocView.newBuilder();
    timeoutDoc.typeName(context.getTypeTable().getAndSaveNicknameFor(TypeRef.of(Type.TYPE_INT32)));
    timeoutDoc.paramName(context.getNamer().varName(timeoutMillisName));
    // TODO figure out a reliable way to line-wrap comments across all languages
    // instead of encoding it in the transformer
    String timeoutMillisDocText =
        String.format(
            "Timeout to use for this call. Only used if %s\nis not set.",
            context.getNamer().varReference(retrySettingsName));
    List<String> timeoutMillisDocLines = context.getNamer().getDocLines(timeoutMillisDocText);
    timeoutDoc.firstLine(timeoutMillisDocLines.get(0));
    timeoutDoc.remainingLines(timeoutMillisDocLines.subList(1, timeoutMillisDocLines.size()));
    arrayKeyDocs.add(timeoutDoc.build());

    paramDoc.arrayKeyDocs(arrayKeyDocs);

    return paramDoc.build();
  }
}