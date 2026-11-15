# DashLink — Third-party notices

확인일: **2026-11-14**. 이 문서는 DashLink 제출 소스에서 참조하는 제3자 구성요소의 버전, 확인한 라이선스 선언과 출처를 기록한다. DashLink 자체 코드의 라이선스를 새로 부여하거나 Pleos SDK의 재배포 권한을 대신하는 문서는 아니다.

## 확인 범위와 재현

실제 프로젝트에서 아래 명령을 실행해 성공한 결과를 기준으로 작성했다. `->`로 대체된 의존성은 최종 선택 버전을 적었다.

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain
./gradlew :app:dependencies --configuration releaseUnitTestRuntimeClasspath --console=plain
```

로컬 Gradle 캐시의 POM, JAR 안의 POM·라이선스, Gradle Wrapper의 라이선스를 먼저 읽었다. 캐시에 없는 POM은 공식 배포 저장소인 Google Maven과 Maven Central에서 확인했다. 이 목록은 해결된 의존성 그래프이며, BOM·플랫폼 메타데이터·Lint 전용 항목까지 각각 APK에 실행 코드로 포함된다는 뜻은 아니다. 개별 라이브러리 소스를 수정하거나 복사해 앱 코드에 넣은 변경은 이번 작업에서 하지 않았다.

소스에는 앱 소스와 Gradle Wrapper를 포함하고, **Pleos SDK AAR/JAR, Android SDK, 에뮬레이터 이미지, Gradle 배포 ZIP·캐시, `.local/`, 빌드 산출물은 포함하지 않는다.** 의존성은 재현 빌드 시 공식 저장소에서 받는다. 서명된 제출 APK는 `apk/`에 별도로 포함한다. 소스에서 SDK 파일을 제외한다는 사실만으로 APK 배포에 필요한 SDK 권리가 확인된 것은 아니다.

## Pleos SDK — 라이선스 확인 필요

| 의존성 | 구분 | 확인된 라이선스 상태 |
| --- | --- | --- |
| `ai.pleos.playground:Vehicle:2.3.2` | 직접 SDK | POM에 라이선스명·URL 선언 없음 |
| `ai.pleos.playground:FusedLocation:1.1.3` | 직접 SDK | POM에 라이선스명·URL 선언 없음 |
| `ai.pleos.playground:vehicle-aidl:1.1.1` | Vehicle 전이 모듈 | POM에 라이선스명·URL 선언 없음 |
| `ai.pleos.playground:vehicle-data:1.1.11` | Vehicle 전이 모듈 | POM에 라이선스명·URL 선언 없음 |
| `ai.pleos.playground:fused-location-aidl:0.0.5` | FusedLocation 전이 모듈 | POM에 라이선스명·URL 선언 없음 |
| `ai.pleos.playground:fused-location-data:0.0.3` | FusedLocation 전이 모듈 | POM에 라이선스명·URL 선언 없음 |

직접 SDK 두 AAR의 최상위 파일 목록에서도 `LICENSE`·`NOTICE` 파일을 확인하지 못했다. 이들 모듈을 Apache-2.0 등 오픈소스로 분류하지 않는다. **공식 SDK 이용약관과 소스·바이너리·APK 재배포 범위 확인이 필요하다.** 공개 SDK를 공모전 에뮬레이터 개발·시험에 사용할 수 있다는 공지를 SDK 바이너리의 자유 재배포 허가로 확대 해석하지 않는다.

공식 출처: [Vehicle 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup), [FusedLocation 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup), [SDK 정책](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/), [Pleos 서비스 이용약관](https://pleos.ai/playground/external/use-policy?partnerId=672505ad-2e37-4500-8162-000bcfc05b5c&locale=ko-KR), [공모전 SDK 안내](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008).

직접 확인한 배포 메타데이터: [Vehicle 2.3.2 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom), [FusedLocation 1.1.3 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3.pom).

## 오픈소스 런타임 의존성

아래 라이선스는 배포 POM의 선언을 확인한 결과다. `javax.inject`는 네임스페이스가 없는 원본 POM의 라이선스 선언과 공식 프로젝트를 함께 확인했다. `listenablefuture`는 부모 POM `guava-parent:26.0-android`의 라이선스를 따른다.

| 구성요소와 최종 버전 | 라이선스 | 공식 근거 |
| --- | --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.20`, `kotlin-stdlib-common:2.2.20` | Apache-2.0 | [stdlib POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.20/kotlin-stdlib-2.2.20.pom), [common POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-common/2.2.20/kotlin-stdlib-common-2.2.20.pom) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0`, `kotlin-stdlib-jdk8:1.8.0` | Apache-2.0 | [jdk7 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.8.0/kotlin-stdlib-jdk7-1.8.0.pom), [jdk8 POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.8.0/kotlin-stdlib-jdk8-1.8.0.pom) |
| `org.jetbrains.kotlin:kotlin-reflect:1.9.25` | Apache-2.0 | [POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.9.25/kotlin-reflect-1.9.25.pom) |
| `org.jetbrains:annotations:13.0` | Apache-2.0 | JAR 내 `META-INF/maven/org.jetbrains/annotations/pom.xml`, [배포 POM](https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.3`, `kotlinx-coroutines-core:1.6.3`, `kotlinx-coroutines-core-jvm:1.6.3` | Apache-2.0 | [Android POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.6.3/kotlinx-coroutines-android-1.6.3.pom), [core POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.6.3/kotlinx-coroutines-core-1.6.3.pom), [JVM POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.3/kotlinx-coroutines-core-jvm-1.6.3.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.3` — 버전 제약 메타데이터 | Apache-2.0 | [BOM POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-bom/1.6.3/kotlinx-coroutines-bom-1.6.3.pom) |
| `com.google.dagger:dagger:2.51.1`, `hilt-android:2.51.1`, `hilt-core:2.51.1`, `dagger-lint-aar:2.51.1` | Apache-2.0 | 각 배포 POM 및 [Dagger 공식 저장소](https://github.com/google/dagger) |
| `com.google.code.gson:gson:2.10.1` | Apache-2.0 | JAR 내 `META-INF/maven/com.google.code.gson/gson/pom.xml`, [배포 POM](https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.pom) |
| `com.google.code.findbugs:jsr305:3.0.2` | Apache-2.0 | JAR 내 `META-INF/maven/com.google.code.findbugs/jsr305/pom.xml`, [배포 POM](https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.pom) |
| `javax.inject:javax.inject:1` | Apache-2.0 | [POM](https://repo.maven.apache.org/maven2/javax/inject/javax.inject/1/javax.inject-1.pom), [JSR-330 공식 프로젝트](https://github.com/javax-inject/javax-inject) |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 | [POM](https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/1.0/listenablefuture-1.0.pom), [부모 POM](https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/26.0-android/guava-parent-26.0-android.pom) |

### AndroidX — 모두 Apache-2.0

각 항목의 POM에서 Apache License 2.0 선언을 확인했다. 다음 표에는 release 그래프에 나타난 AndroidX 모듈을 빠짐없이 적었다.

| 좌표 | 최종 버전 | 공식 POM |
| --- | --- | --- |
| `androidx.activity:activity` | 1.5.1 | [POM](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.5.1/activity-1.5.1.pom) |
| `androidx.annotation:annotation` | 1.2.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.2.0/annotation-1.2.0.pom) |
| `androidx.annotation:annotation-experimental` | 1.3.1 | [POM](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-experimental/1.3.1/annotation-experimental-1.3.1.pom) |
| `androidx.collection:collection` | 1.1.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/collection/collection/1.1.0/collection-1.1.0.pom) |
| `androidx.core:core`, `androidx.core:core-ktx` | 1.8.0 | [core POM](https://dl.google.com/dl/android/maven2/androidx/core/core/1.8.0/core-1.8.0.pom), [ktx POM](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.8.0/core-ktx-1.8.0.pom) |
| `androidx.concurrent:concurrent-futures` | 1.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/concurrent/concurrent-futures/1.0.0/concurrent-futures-1.0.0.pom) |
| `androidx.arch.core:core-common`, `androidx.arch.core:core-runtime` | 2.1.0 | [common POM](https://dl.google.com/dl/android/maven2/androidx/arch/core/core-common/2.1.0/core-common-2.1.0.pom), [runtime POM](https://dl.google.com/dl/android/maven2/androidx/arch/core/core-runtime/2.1.0/core-runtime-2.1.0.pom) |
| `androidx.lifecycle:lifecycle-common`, `lifecycle-runtime`, `lifecycle-viewmodel`, `lifecycle-viewmodel-savedstate`, `lifecycle-livedata-core` | 2.5.1 | 각 모듈 POM; [common POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-common/2.5.1/lifecycle-common-2.5.1.pom) |
| `androidx.lifecycle:lifecycle-livedata` | 2.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata/2.0.0/lifecycle-livedata-2.0.0.pom) |
| `androidx.savedstate:savedstate` | 1.2.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate/1.2.0/savedstate-1.2.0.pom) |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | [POM](https://dl.google.com/dl/android/maven2/androidx/versionedparcelable/versionedparcelable/1.1.1/versionedparcelable-1.1.1.pom) |
| `androidx.tracing:tracing` | 1.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/tracing/tracing/1.0.0/tracing-1.0.0.pom) |
| `androidx.fragment:fragment` | 1.5.1 | [POM](https://dl.google.com/dl/android/maven2/androidx/fragment/fragment/1.5.1/fragment-1.5.1.pom) |
| `androidx.loader:loader` | 1.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/loader/loader/1.0.0/loader-1.0.0.pom) |
| `androidx.viewpager:viewpager` | 1.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/viewpager/viewpager/1.0.0/viewpager-1.0.0.pom) |
| `androidx.customview:customview` | 1.0.0 | [POM](https://dl.google.com/dl/android/maven2/androidx/customview/customview/1.0.0/customview-1.0.0.pom) |

## 소스에 포함되는 빌드 도구와 테스트 의존성

| 구성요소 | 버전·용도 | 라이선스·근거 |
| --- | --- | --- |
| Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) | Gradle 8.13 배포를 사용하도록 설정된 빌드 진입점. 소스 압축에 포함 | Apache-2.0. 스크립트의 기존 저작권 헤더와 Wrapper JAR의 `META-INF/LICENSE`를 유지한다. [공식 Wrapper 소스](https://github.com/gradle/gradle/tree/v8.13.0/platforms/core-runtime/wrapper-main) |
| Gradle 배포물 | 8.13, Wrapper가 별도 다운로드. 소스 압축에 미포함 | Gradle 배포물의 `LICENSE`와 `NOTICE`를 확인했다. 배포 ZIP에는 별도 라이선스의 다른 구성요소도 포함되므로 전체 배포물을 단일 Apache-2.0 구성요소로 재분류하지 않는다. [Gradle 라이선스](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE), [NOTICE](https://github.com/gradle/gradle/blob/v8.13.0/NOTICE) |
| Android Gradle Plugin | `com.android.tools.build:gradle:8.11.1`, 빌드 도구 | Apache-2.0. [공식 POM](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.11.1/gradle-8.11.1.pom) |
| Kotlin Gradle Plugin | `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20`, 빌드 도구 | Apache-2.0. [POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.2.20/kotlin-gradle-plugin-2.2.20.pom) |
| JUnit 4 | `junit:junit:4.13.2`, JVM 테스트 전용 | EPL-1.0. [POM](https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom), [해당 버전 공식 라이선스·소스](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) |
| Hamcrest Core | `org.hamcrest:hamcrest-core:1.3`, JUnit의 테스트 전이 의존성 | BSD-3-Clause. 부모 POM의 New BSD 선언과 실제 JAR의 `LICENSE.txt` 확인. [부모 POM](https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-parent/1.3/hamcrest-parent-1.3.pom), [공식 프로젝트](https://hamcrest.org/JavaHamcrest/) |

JUnit·Hamcrest는 release 앱의 런타임 의존성이 아니다. 빌드 도구의 모든 내부 전이 의존성을 앱 런타임 목록으로 전개하지 않았다. Android SDK와 Pleos 시스템 이미지는 빌드/실행 환경이며 소스에 재배포하지 않는다.

## 보존한 라이선스 원문

아래 원문은 로컬 배포 파일에서 읽어 보존했다. Apache-2.0은 Wrapper JAR의 `META-INF/LICENSE`, EPL-1.0은 JUnit JAR의 `LICENSE-junit.txt`, BSD-3-Clause는 Hamcrest JAR의 `LICENSE.txt`가 원본이다. 공식 기준 문서는 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl/epl-v10.html)에서 확인할 수 있다.

### Apache License 2.0

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS
```

### Eclipse Public License 1.0 — JUnit 4.13.2

```text
JUnit

Eclipse Public License - v 1.0

THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE PUBLIC
LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF THE PROGRAM
CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.

1. DEFINITIONS

"Contribution" means:

      a) in the case of the initial Contributor, the initial code and
         documentation distributed under this Agreement, and
      b) in the case of each subsequent Contributor:

      i) changes to the Program, and

      ii) additions to the Program;

      where such changes and/or additions to the Program originate from and are
distributed by that particular Contributor. A Contribution 'originates' from a
Contributor if it was added to the Program by such Contributor itself or anyone
acting on such Contributor's behalf. Contributions do not include additions to
the Program which: (i) are separate modules of software distributed in
conjunction with the Program under their own license agreement, and (ii) are
not derivative works of the Program.

"Contributor" means any person or entity that distributes the Program.

"Licensed Patents " mean patent claims licensable by a Contributor which are
necessarily infringed by the use or sale of its Contribution alone or when
combined with the Program.

"Program" means the Contributions distributed in accordance with this Agreement.

"Recipient" means anyone who receives the Program under this Agreement,
including all Contributors.

2. GRANT OF RIGHTS

      a) Subject to the terms of this Agreement, each Contributor hereby grants
Recipient a non-exclusive, worldwide, royalty-free copyright license to
reproduce, prepare derivative works of, publicly display, publicly perform,
distribute and sublicense the Contribution of such Contributor, if any, and
such derivative works, in source code and object code form.

      b) Subject to the terms of this Agreement, each Contributor hereby grants
Recipient a non-exclusive, worldwide, royalty-free patent license under
Licensed Patents to make, use, sell, offer to sell, import and otherwise
transfer the Contribution of such Contributor, if any, in source code and
object code form. This patent license shall apply to the combination of the
Contribution and the Program if, at the time the Contribution is added by the
Contributor, such addition of the Contribution causes such combination to be
covered by the Licensed Patents. The patent license shall not apply to any
other combinations which include the Contribution. No hardware per se is
licensed hereunder.

      c) Recipient understands that although each Contributor grants the
licenses to its Contributions set forth herein, no assurances are provided by
any Contributor that the Program does not infringe the patent or other
intellectual property rights of any other entity. Each Contributor disclaims
any liability to Recipient for claims brought by any other entity based on
infringement of intellectual property rights or otherwise. As a condition to
exercising the rights and licenses granted hereunder, each Recipient hereby
assumes sole responsibility to secure any other intellectual property rights
needed, if any. For example, if a third party patent license is required to
allow Recipient to distribute the Program, it is Recipient's responsibility to
acquire that license before distributing the Program.

      d) Each Contributor represents that to its knowledge it has sufficient
copyright rights in its Contribution, if any, to grant the copyright license
set forth in this Agreement.

3. REQUIREMENTS

A Contributor may choose to distribute the Program in object code form under
its own license agreement, provided that:

      a) it complies with the terms and conditions of this Agreement; and

      b) its license agreement:

      i) effectively disclaims on behalf of all Contributors all warranties and
conditions, express and implied, including warranties or conditions of title
and non-infringement, and implied warranties or conditions of merchantability
and fitness for a particular purpose;

      ii) effectively excludes on behalf of all Contributors all liability for
damages, including direct, indirect, special, incidental and consequential
damages, such as lost profits;

      iii) states that any provisions which differ from this Agreement are
offered by that Contributor alone and not by any other party; and

      iv) states that source code for the Program is available from such
Contributor, and informs licensees how to obtain it in a reasonable manner on
or through a medium customarily used for software exchange.

When the Program is made available in source code form:

      a) it must be made available under this Agreement; and

      b) a copy of this Agreement must be included with each copy of the
Program.

Contributors may not remove or alter any copyright notices contained within the
Program.

Each Contributor must identify itself as the originator of its Contribution, if
any, in a manner that reasonably allows subsequent Recipients to identify the
originator of the Contribution.

4. COMMERCIAL DISTRIBUTION

Commercial distributors of software may accept certain responsibilities with
respect to end users, business partners and the like. While this license is
intended to facilitate the commercial use of the Program, the Contributor who
includes the Program in a commercial product offering should do so in a manner
which does not create potential liability for other Contributors. Therefore, if
a Contributor includes the Program in a commercial product offering, such
Contributor ("Commercial Contributor") hereby agrees to defend and indemnify
every other Contributor ("Indemnified Contributor") against any losses, damages
and costs (collectively "Losses") arising from claims, lawsuits and other legal
actions brought by a third party against the Indemnified Contributor to the
extent caused by the acts or omissions of such Commercial Contributor in
connection with its distribution of the Program in a commercial product
offering. The obligations in this section do not apply to any claims or Losses
relating to any actual or alleged intellectual property infringement. In order
to qualify, an Indemnified Contributor must: a) promptly notify the Commercial
Contributor in writing of such claim, and b) allow the Commercial Contributor
to control, and cooperate with the Commercial Contributor in, the defense and
any related settlement negotiations. The Indemnified Contributor may
participate in any such claim at its own expense.

For example, a Contributor might include the Program in a commercial product
offering, Product X. That Contributor is then a Commercial Contributor. If that
Commercial Contributor then makes performance claims, or offers warranties
related to Product X, those performance claims and warranties are such
Commercial Contributor's responsibility alone. Under this section, the
Commercial Contributor would have to defend claims against the other
Contributors related to those performance claims and warranties, and if a court
requires any other Contributor to pay any damages as a result, the Commercial
Contributor must pay those damages.

5. NO WARRANTY

EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, THE PROGRAM IS PROVIDED ON AN
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR
IMPLIED INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR CONDITIONS OF TITLE,
NON-INFRINGEMENT, MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Each
Recipient is solely responsible for determining the appropriateness of using
and distributing the Program and assumes all risks associated with its exercise
of rights under this Agreement, including but not limited to the risks and
costs of program errors, compliance with applicable laws, damage to or loss of
data, programs or equipment, and unavailability or interruption of operations.

6. DISCLAIMER OF LIABILITY

EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, NEITHER RECIPIENT NOR ANY
CONTRIBUTORS SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING WITHOUT LIMITATION LOST
PROFITS), HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
WAY OUT OF THE USE OR DISTRIBUTION OF THE PROGRAM OR THE EXERCISE OF ANY RIGHTS
GRANTED HEREUNDER, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

7. GENERAL

If any provision of this Agreement is invalid or unenforceable under applicable
law, it shall not affect the validity or enforceability of the remainder of the
terms of this Agreement, and without further action by the parties hereto, such
provision shall be reformed to the minimum extent necessary to make such
provision valid and enforceable.

If Recipient institutes patent litigation against any
entity (including a cross-claim or counterclaim in a lawsuit) alleging that the
Program itself (excluding combinations of the Program with other software or
hardware) infringes such Recipient's patent(s), then such Recipient's rights
granted under Section 2(b) shall terminate as of the date such litigation is
filed.

All Recipient's rights under this Agreement shall terminate if it fails to
comply with any of the material terms or conditions of this Agreement and does
not cure such failure in a reasonable period of time after becoming aware of
such noncompliance. If all Recipient's rights under this Agreement terminate,
Recipient agrees to cease use and distribution of the Program as soon as
reasonably practicable. However, Recipient's obligations under this Agreement
and any licenses granted by Recipient relating to the Program shall continue
and survive.

Everyone is permitted to copy and distribute copies of this Agreement, but in
order to avoid inconsistency the Agreement is copyrighted and may only be
modified in the following manner. The Agreement Steward reserves the right to
publish new versions (including revisions) of this Agreement from time to time.
No one other than the Agreement Steward has the right to modify this Agreement.
The Eclipse Foundation is the initial Agreement Steward. The Eclipse Foundation may assign the responsibility to
serve as the Agreement Steward to a suitable separate entity. Each new version
of the Agreement will be given a distinguishing version number. The Program
(including Contributions) may always be distributed subject to the version of
the Agreement under which it was received. In addition, after a new version of
the Agreement is published, Contributor may elect to distribute the Program
(including its Contributions) under the new version. Except as expressly stated
in Sections 2(a) and 2(b) above, Recipient receives no rights or licenses to
the intellectual property of any Contributor under this Agreement, whether
expressly, by implication, estoppel or otherwise. All rights in the Program not
expressly granted under this Agreement are reserved.

This Agreement is governed by the laws of the State of New York and the
intellectual property laws of the United States of America. No party to this
Agreement will bring a legal action under this Agreement more than one year
after the cause of action arose. Each party waives its rights to a jury trial
in any resulting litigation.
```

### BSD 3-Clause — Hamcrest Core 1.3

```text
BSD License

Copyright (c) 2000-2006, www.hamcrest.org
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of
conditions and the following disclaimer. Redistributions in binary form must reproduce
the above copyright notice, this list of conditions and the following disclaimer in
the documentation and/or other materials provided with the distribution.

Neither the name of Hamcrest nor the names of its contributors may be used to endorse
or promote products derived from this software without specific prior written
permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
DAMAGE.
```
