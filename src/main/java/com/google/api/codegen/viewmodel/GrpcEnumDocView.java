/* Copyright 2017 Google LLC
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
package com.google.api.codegen.viewmodel;

import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
public abstract class GrpcEnumDocView implements GrpcElementDocView {
  public abstract String name();

  public abstract List<String> lines();

  public abstract List<GrpcEnumValueDocView> values();

  public abstract String packageName();

  public static Builder newBuilder() {
    return new AutoValue_GrpcEnumDocView.Builder();
  }

  public String type() {
    return GrpcEnumDocView.class.getSimpleName();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder name(String val);

    public abstract Builder lines(List<String> val);

    public abstract Builder values(List<GrpcEnumValueDocView> val);

    public abstract Builder packageName(String val);

    public abstract GrpcEnumDocView build();
  }
}
