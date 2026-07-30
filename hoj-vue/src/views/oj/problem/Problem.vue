<template>
  <div :class="bodyClass">
    <div id="problem-main">
      <!--problem main-->
      <el-row class="problem-box" 
        :id="'problem-box' + '-' + $route.name">
        <el-col
          :sm="24"
          :md="12"
          :lg="12"
          class="problem-left"
          :id="'problem-left'+'-'+ $route.name"
        >
          <el-tabs
            v-model="activeName"
            type="border-card"
            @tab-click="handleClickTab"
          >
            <el-tab-pane
              name="problemDetail"
              v-loading="loading"
            >
              <template #label>
                <span>
                  <i
                    class="fa fa-list-alt problem-menu-icon"
                    aria-hidden="true"
                  ></i>
                  {{ $t('m.Problem_Description') }}
                </span>
              </template>
              <div
                :padding="10"
                shadow
                :id="'js-left'+'-'+ $route.name"
                class="js-left"
              >
                <div
                  class="panel-title"
                >
                  <span>{{ problemData.problem.title }}</span><br />
                  <div class="problem-tag">
                    <span v-if="problemData.problem.isFileIO"
                      style="padding-right: 10px">
                      <el-popover
                        placement="bottom"
                        trigger="hover"
                      >
                      <template #reference>
                        <el-tag
                              size="default"
                            type="warning"
                            style="cursor: pointer;"
                            effect="dark"
                        ><i class="el-icon-document"> {{ $t('m.File_IO') }}</i>
                        </el-tag>
                      </template>
                      <table style="white-space: nowrap;">
                        <tbody>
                          <tr>
                            <td align="right" style="padding-right: 10px">
                            <strong>{{ $t('m.Input_File') }}</strong>
                            </td>
                            <td>{{ problemData.problem.ioReadFileName }}</td>
                          </tr>
                          <tr>
                            <td align="right" style="padding-right: 10px">
                              <strong>{{ $t('m.Output_File') }}</strong>
                            </td>
                            <td>{{ problemData.problem.ioWriteFileName }}</td>
                          </tr>
                        </tbody>
                      </table>
                      </el-popover>
                    </span>
                    <span v-if="contestID && !contestEnded">
                      <el-tag
                        effect="plain"
                        size="default"
                      >{{
                        $t('m.Contest_Problem')
                      }}</el-tag>
                    </span>
                    <span
                      v-else-if="problemData.tags.length > 0"
                    >
                      <el-popover
                        placement="right-start"
                        width="60"
                        trigger="hover"
                      >
                        <template #reference>
                          <el-tag
                            size="default"
                            class="show-tags-trigger"
                            type="primary"
                            style="cursor: pointer;"
                            effect="plain"
                          >{{ $t('m.Show_Tags') }} <i class="el-icon-caret-bottom"></i></el-tag>
                        </template>
                        <el-tag
                          v-for="(tag, index) in problemData.tags"
                          :key="index"
                          size="small"
                          class="problem-topic-tag"
                          :color="tag.color ? tag.color : '#409eff'"
                          effect="dark"
                          style="margin-right:5px;margin-top:2px"
                        >{{ tag.name }}</el-tag>
                      </el-popover>
                    </span>
                    <span
                      v-else-if="problemData.tags.length == 0"
                    >
                      <el-tag
                        effect="plain"
                        size="default"
                      >{{
                        $t('m.No_tag')
                      }}</el-tag>
                    </span>
                  </div>
                  
                  <div class="problem-menu">
                    <span v-if="isShowProblemDiscussion">
                      <el-link
                        type="primary"
                        underline="never"
                        @click="goProblemDiscussion"
                      ><i
                          class="fa fa-comments problem-menu-icon"
                          aria-hidden="true"
                        ></i>
                        {{ $t('m.Problem_Discussion') }}</el-link>
                    </span>
                    <span>
                      <el-link
                        type="primary"
                        underline="never"
                        @click="graphVisible = !graphVisible"
                      ><i
                          class="fa fa-pie-chart problem-menu-icon"
                          aria-hidden="true"
                        ></i>
                        {{ $t('m.Statistic') }}</el-link>
                    </span>
                    <span>
                      <el-link
                        type="primary"
                        underline="never"
                        @click="goProblemSubmission"
                      ><i
                          class="fa fa-bars problem-menu-icon"
                          aria-hidden="true"
                        ></i>
                        {{ $t('m.Solutions') }}</el-link>
                    </span>
                  </div>
                  <div class="question-intr">
                    <template v-if="!isCFProblem">
                      <span>{{ $t('m.Time_Limit') }}：C/C++
                        {{ problemData.problem.timeLimit }}MS，{{
                          $t('m.Other')
                        }}
                        {{ problemData.problem.timeLimit * 2 }}MS</span><br />
                      <span>{{ $t('m.Memory_Limit') }}：C/C++
                        {{ problemData.problem.memoryLimit }}MB，{{
                          $t('m.Other')
                        }}
                        {{ problemData.problem.memoryLimit * 2 }}MB</span><br />
                    </template>

                    <template v-else>
                      <span>{{ $t('m.Time_Limit') }}：{{
                          problemData.problem.timeLimit
                        }}MS</span>
                      <br />
                      <span>{{ $t('m.Memory_Limit') }}：{{
                          problemData.problem.memoryLimit
                        }}MB</span><br />
                    </template>
                    <template v-if="problemData.problem.difficulty != null">
                      <span>{{ $t('m.Level') }}：<span
                          class="el-tag el-tag--small problem-difficulty-tag"
                          :style="getLevelColor(problemData.problem.difficulty)"
                        >{{
                            getLevelName(problemData.problem.difficulty)
                          }}</span></span>
                      <br />
                    </template>
                    <template v-if="problemData.problem.type == 1">
                      <span>{{ $t('m.Score') }}：{{ problemData.problem.ioScore }}
                      </span>
                      <span
                        v-if="!contestID"
                        style="margin-left:5px;"
                      >
                        {{ $t('m.OI_Rank_Score') }}：{{
                          calcOIRankScore(
                            problemData.problem.ioScore,
                            problemData.problem.difficulty
                          )
                        }}(0.1*{{ $t('m.Score') }}+2*{{ $t('m.Level') }})
                      </span>
                      <br />
                    </template>

                    <template v-if="problemData.problem.author">
                      <span>{{ $t('m.Created') }}：<el-link
                          type="info"
                          class="author-name"
                          @click="goUserHome(problemData.problem.author)"
                        >{{ problemData.problem.author }}</el-link></span><br />
                    </template>
                  </div>
                </div>

                <div id="problem-content">
                  <template v-if="problemData.problem.description">
                    <p class="title">{{ $t('m.Description') }}</p>
                    <Markdown 
                      class="md-content"
                      :isAvoidXss="problemData.problem.gid != null" 
                      :content="problemData.problem.description">
                    </Markdown>
                  </template>

                  <template v-if="problemData.problem.input">
                    <p class="title">{{ $t('m.Input') }}</p>
                    <Markdown 
                      class="md-content"
                      :isAvoidXss="problemData.problem.gid != null" 
                      :content="problemData.problem.input">
                    </Markdown>
                  </template>

                  <template v-if="problemData.problem.output">
                    <p class="title">{{ $t('m.Output') }}</p>
                    <Markdown 
                      class="md-content"
                      :isAvoidXss="problemData.problem.gid != null" 
                      :content="problemData.problem.output">
                    </Markdown>
                  </template>

                  <template v-if="problemData.problem.examples">
                    <div
                      v-for="(example, index) of problemData.problem.examples"
                      :key="index"
                    >
                      <div class="flex-container example">
                        <div class="example-input">
                          <p class="title">
                            {{ $t('m.Sample_Input') }} {{ index + 1 }}
                            <a
                              class="copy"
                              role="button"
                              tabindex="0"
                              :aria-label="$t('m.Copy') + ' ' + $t('m.Sample_Input')"
                              v-clipboard:copy="example.input"
                              v-clipboard:success="onCopy"
                              v-clipboard:error="onCopyError"
                              @keydown.enter.prevent="$event.currentTarget.click()"
                              @keydown.space.prevent="$event.currentTarget.click()"
                            >
                              <i class="el-icon-document-copy" aria-hidden="true"></i>
                            </a>
                          </p>
                          <pre>{{ example.input }}</pre>
                        </div>
                        <div class="example-output">
                          <p class="title">
                            {{ $t('m.Sample_Output') }} {{ index + 1 }}
                            <a
                              class="copy"
                              role="button"
                              tabindex="0"
                              :aria-label="$t('m.Copy') + ' ' + $t('m.Sample_Output')"
                              v-clipboard:copy="example.output"
                              v-clipboard:success="onCopy"
                              v-clipboard:error="onCopyError"
                              @keydown.enter.prevent="$event.currentTarget.click()"
                              @keydown.space.prevent="$event.currentTarget.click()"
                            >
                              <i class="el-icon-document-copy" aria-hidden="true"></i>
                            </a>
                          </p>
                          <pre>{{ example.output }}</pre>
                        </div>
                      </div>
                    </div>
                  </template>

                  <template v-if="problemData.problem.hint">
                    <p class="title">{{ $t('m.Hint') }}</p>
                    <el-card dis-hover>
                      <Markdown 
                      class="hint-content"
                      :isAvoidXss="problemData.problem.gid != null" 
                      :content="problemData.problem.hint">
                    </Markdown>
                    </el-card>
                  </template>

                  <template v-if="problemData.problem.source && !contestID">
                    <p class="title">{{ $t('m.Source') }}</p>
                    <template v-if="problemData.problem.gid != null">
                      <p
                      class="md-content"
                      v-dompurify-html="problemData.problem.source"
                      ></p>
                    </template>
                    <template v-else>
                      <p
                      class="md-content"
                      v-dompurify-html="problemData.problem.source"
                      ></p>
                    </template>
                  </template>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane name="mySubmission">
              <template #label>
                <span><i class="el-icon-time"></i> {{ $t('m.My_Submission') }}</span>
              </template>
              <template v-if="!isAuthenticated">
                <div
                  style="margin:20px 0px;margin-left:-20px;"
                  id="js-submission"
                >
                  <el-alert
                    :title="$t('m.Please_login_first')"
                    type="warning"
                    center
                    :closable="false"
                    :description="$t('m.Login_to_view_your_submission_history')"
                    show-icon
                  >
                  </el-alert>
                </div>
              </template>
              <template v-else>
                <div
                  style="margin-right:10px;"
                  id="js-submission"
                >
                  <vxe-table
                    align="center"
                    :data="mySubmissions"
                    stripe
                    auto-resize
                    border="inner"
                    :loading="loadingTable"
                  >
                    <vxe-table-column
                      :title="$t('m.Submit_Time')"
                      min-width="96"
                    >
                      <template v-slot="{ row }">
                        <span>
                          <el-tooltip
                            :content="$filters.localtime(row.submitTime)"
                            placement="top"
                          >
                            <span>{{ $filters.fromNow(row.submitTime) }}</span>
                          </el-tooltip>
                        </span>
                      </template>
                    </vxe-table-column>
                    <vxe-table-column
                      field="status"
                      :title="$t('m.Status')"
                      min-width="160"
                    >
                      <template v-slot="{ row }">
                        <span :class="getStatusColor(row.status)">{{
                          JUDGE_STATUS[row.status].name
                        }}</span>
                      </template>
                    </vxe-table-column>
                    <vxe-table-column
                      :title="$t('m.Time')"
                      min-width="96"
                    >
                      <template v-slot="{ row }">
                        <span>{{ submissionTimeFormat(row.time) }}</span>
                      </template>
                    </vxe-table-column>
                    <vxe-table-column
                      :title="$t('m.Memory')"
                      min-width="96"
                    >
                      <template v-slot="{ row }">
                        <span>{{ submissionMemoryFormat(row.memory) }}</span>
                      </template>
                    </vxe-table-column>
                    <vxe-table-column
                      :title="$t('m.Score')"
                      min-width="64"
                      v-if="problemData.problem.type == 1"
                    >
                      <template v-slot="{ row }">
                        <template v-if="contestID && row.score != null">
                          <el-tag
                            effect="plain"
                            size="default"
                            :type="JUDGE_STATUS[row.status]['type']"
                          >{{ row.score }}</el-tag>
                        </template>
                        <template v-else-if="row.score != null">
                          <el-tooltip placement="top">
                            <template #content>
                              <div>
                                {{ $t('m.Problem_Score') }}：{{
                                  row.score != null ? row.score : $t('m.Unknown')
                                }}<br />{{ $t('m.OI_Rank_Score') }}：{{
                                  row.oiRankScore != null
                                    ? row.oiRankScore
                                    : $t('m.Unknown')
                                }}<br />
                                {{
                                  $t('m.OI_Rank_Calculation_Rule')
                                }}：(score*0.1+difficulty*2)
                              </div>
                            </template>
                            <el-tag
                              effect="plain"
                              size="default"
                              :type="JUDGE_STATUS[row.status]['type']"
                            >{{ row.score }}</el-tag>
                          </el-tooltip>
                        </template>
                        <template v-else-if="
                            row.status == JUDGE_STATUS_RESERVE['Pending'] ||
                              row.status == JUDGE_STATUS_RESERVE['Compiling'] ||
                              row.status == JUDGE_STATUS_RESERVE['Judging']
                          ">
                          <el-tag
                            effect="plain"
                            size="default"
                            :type="JUDGE_STATUS[row.status]['type']"
                          >
                            <i class="el-icon-loading"></i>
                          </el-tag>
                        </template>
                        <template v-else>
                          <el-tag
                            effect="plain"
                            size="default"
                            :type="JUDGE_STATUS[row.status]['type']"
                          >--</el-tag>
                        </template>
                      </template>
                    </vxe-table-column>
                    <vxe-table-column
                      field="language"
                      :title="$t('m.Language')"
                      show-overflow
                      min-width="130"
                    >
                      <template v-slot="{ row }">
                        <el-tooltip
                          class="item"
                          effect="dark"
                          :content="$t('m.View_submission_details')"
                          placement="top"
                        >
                          <el-button
                            type="text"
                            @click="showSubmitDetail(row)"
                          >{{ row.language }}</el-button>
                        </el-tooltip>
                      </template>
                    </vxe-table-column>
                  </vxe-table>
                  <Pagination
                    :total="mySubmission_total"
                    :page-size="mySubmission_limit"
                    @on-change="getMySubmission"
                    v-model:current="mySubmission_currentPage"
                  ></Pagination>
                </div>
              </template>
            </el-tab-pane>

            <el-tab-pane
              name="extraFile"
              v-if="userExtraFile"
            >
              <template #label>
                <span><i class="fa fa-file-code-o"> {{ $t('m.Problem_Annex') }}</i>
                </span>
              </template>
              <div id="js-extraFile">
                <el-divider></el-divider>
                <div>
                  <el-tag
                    :key="index"
                    v-for="(value, key, index) in userExtraFile"
                    class="extra-file"
                    :disable-transitions="false"
                    @click="showExtraFileContent(key, value)"
                  >
                    <i class="fa fa-file-code-o"> {{ key }}</i>
                  </el-tag>
                </div>
                <el-divider></el-divider>

                <div
                  class="markdown-body"
                  v-if="fileContent"
                >
                  <h3>
                    {{ fileName }}
                    <el-button
                      type="primary"
                      :icon="legacyElementIcons['el-icon-download']"
                      :aria-label="$t('m.Download')"
                      size="small"
                      circle
                      @click="downloadExtraFile"
                      class="file-download"
                    ></el-button>
                  </h3>
                  <pre v-highlight="fileContent"><code class="c++"></code></pre>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-col>
        <div
          class="problem-resize hidden-sm-and-down"
          :id="'js-center'+'-'+ $route.name"
          :title="$t('m.Shrink_Sidebar')"
          role="separator"
          aria-orientation="vertical"
          :aria-label="$t('m.Shrink_Sidebar')"
          @mousedown="startResize"
        >
          <span
            class="resize-grip"
            aria-hidden="true"
          >
            <i></i>
            <i></i>
            <i></i>
          </span>
          <span>
            <el-tooltip
              :content="
              toWatchProblem
                ? $t('m.View_Problem_Content')
                : $t('m.Only_View_Problem')
            "
              placement="right"
              v-if="!toResetWatch"
            >
              <el-button
                :icon="legacyElementIcons['el-icon-caret-right']"
                :aria-label="
                  toWatchProblem
                    ? $t('m.View_Problem_Content')
                    : $t('m.Only_View_Problem')
                "
                circle
                class="right-fold fold"
                @click.stop="onlyWatchProblem"
                size="small"
              ></el-button>
            </el-tooltip>
            <el-tooltip
              :content="$t('m.Put_away_the_full_screen_and_write_the_code')"
              placement="left"
              v-else
            >
              <el-button
                :icon="legacyElementIcons['el-icon-caret-left']"
                :aria-label="$t('m.Put_away_the_full_screen_and_write_the_code')"
                circle
                class="left-fold fold"
                @click.stop="resetWatch(false)"
                size="small"
              ></el-button>
            </el-tooltip>
          </span>
        </div>
        <el-col
          :sm="24"
          :md="12"
          :lg="12"
          class="problem-right"
          :id="'problem-right' + '-' + $route.name"
        >
          <el-card
            :padding="10"
            id="submit-code"
            shadow="always"
            class="submit-detail"
          >
            <CodeMirror
              v-model:value="code"
              :languages="problemData.languages"
              v-model:language="language"
              v-model:theme="theme"
              v-model:height="height"
              v-model:fontSize="fontSize"
              v-model:tabSize="tabSize"
              @resetCode="onResetToTemplate"
              @changeTheme="onChangeTheme"
              @changeLang="onChangeLang"
              @getUserLastAccepetedCode="getUserLastAccepetedCode"
              @switchFocusMode="switchFocusMode"
              v-model:openFocusMode="openFocusMode"
              v-model:openTestCaseDrawer="openTestCaseDrawer"
              :problemTestCase="problemData.problem.examples"
              :pid="problemData.problem.id"
              :type="problemType"
              :isAuthenticated="isAuthenticated"
              :isRemoteJudge="problemData.problem.isRemote"
              :submitDisabled="submitDisabled"
            ></CodeMirror>
            <div id="js-right-bottom">
              <el-row>
                <el-col
                  :sm="24"
                  :md="10"
                  :lg="10"
                  style="margin-top:4px;"
                >
                  <div v-if="!isAuthenticated">
                    <el-alert
                      type="info"
                      show-icon
                      effect="dark"
                      :closable="false"
                    >{{ $t('m.Please_login_first') }}</el-alert>
                  </div>
                  <div
                    class="status"
                    v-if="statusVisible"
                  >
                    <template v-if="result.status == JUDGE_STATUS_RESERVE['sf']">
                      <span>{{ $t('m.Status') }}:</span>
                      <el-tag
                        effect="dark"
                        :color="submissionStatus.color"
                        @click="reSubmit(submissionId)"
                      >
                        <i class="el-icon-refresh"></i>
                        {{ submissionStatus.text }}
                      </el-tag>
                    </template>
                    <template v-else-if="result.status == JUDGE_STATUS_RESERVE['snr']">
                      <el-alert
                        type="warning"
                        show-icon
                        effect="dark"
                        :closable="false"
                      >{{ $t('m.Submitted_Not_Result') }}</el-alert>
                    </template>
                    <template v-else-if="
                        !this.contestID ||
                          (this.contestID &&
                            ContestRealTimePermission &&
                            this.contestRuleType == RULE_TYPE.OI) ||
                          (this.contestID &&
                            this.contestRuleType == RULE_TYPE.ACM)
                      ">
                      <span style="font-size: 14px;font-weight: bolder;">{{ $t('m.Status') }}:</span>
                      <el-tooltip
                        class="item"
                        effect="dark"
                        :content="$t('m.View_submission_details')"
                        placement="top"
                      >
                        <el-tag
                          effect="dark"
                          class="submission-status"
                          :color="submissionStatus.color"
                          role="button"
                          tabindex="0"
                          :aria-label="$t('m.View_submission_details')"
                          @click="submissionRoute"
                          @keydown.enter.prevent="submissionRoute"
                          @keydown.space.prevent="submissionRoute"
                        >
                          <template v-if="this.result.status == JUDGE_STATUS_RESERVE['Pending'] 
                          || this.result.status == JUDGE_STATUS_RESERVE['Compiling'] 
                          || this.result.status == JUDGE_STATUS_RESERVE['Judging'] 
                          || this.result.status == JUDGE_STATUS_RESERVE['Submitting']">
                            <i class="el-icon-loading"></i> {{ submissionStatus.text }}
                          </template>
                          <template v-else-if="this.result.status == JUDGE_STATUS_RESERVE.ac">
                            <i class="el-icon-success"> {{ submissionStatus.text }}</i>
                          </template>
                          <template v-else-if="this.result.status == JUDGE_STATUS_RESERVE.pa">
                            <i class="el-icon-remove"> {{ submissionStatus.text }}</i>
                          </template>
                          <template v-else>
                            <i class="el-icon-error"> {{ submissionStatus.text }}</i>
                          </template>
                        </el-tag>
                      </el-tooltip>
                    </template>
                    <template v-else-if="
                        this.contestID &&
                          !ContestRealTimePermission &&
                          this.contestRuleType == RULE_TYPE.OI
                      ">
                      <el-alert
                        type="success"
                        show-icon
                        effect="dark"
                        :closable="false"
                      >{{ $t('m.Submitted_successfully') }}</el-alert>
                    </template>
                  </div>
                  <div v-else-if="
                      (!this.contestID ||
                        this.contestRuleType == RULE_TYPE.ACM) &&
                        problemData.myStatus == JUDGE_STATUS_RESERVE.ac
                    ">
                    <el-alert
                      type="success"
                      show-icon
                      effect="dark"
                      :closable="false"
                    >{{ $t('m.You_have_solved_the_problem') }}</el-alert>
                  </div>
                  <div v-else-if="
                      this.contestID &&
                        !ContestRealTimePermission &&
                        this.contestRuleType == RULE_TYPE.OI &&
                        submissionExists
                    ">
                    <el-alert
                      type="success"
                      show-icon
                      effect="dark"
                      :closable="false"
                    >{{ $t('m.You_have_submitted_a_solution') }}</el-alert>
                  </div>
                  <div v-if="contestEnded && !statusVisible">
                    <el-alert
                      type="warning"
                      show-icon
                      effect="dark"
                      :closable="false"
                    >{{ $t('m.Contest_has_ended') }}</el-alert>
                  </div>
                </el-col>

                <el-col
                  :sm="24"
                  :md="14"
                  :lg="14"
                  style="margin-top:4px;"
                >
                  <template v-if="captchaRequired">
                    <div class="captcha-container">
                      <el-tooltip
                        v-if="captchaRequired"
                        content="Click to refresh"
                        placement="top"
                      >
                        <img
                          :src="captchaSrc"
                          @click="getCaptchaSrc"
                        />
                      </el-tooltip>
                      <el-input
                        v-model="captchaCode"
                        class="captcha-code"
                      />
                    </div>
                  </template>
                  <div class="judge-actions">
                    <el-button
                      v-if="!submitDisabled"
                      type="success"
                      plain
                      class="judge-action-button online-test-button"
                      :class="{ 'online-test-button--active': openTestCaseDrawer }"
                      @click="openTestJudgeDrawer"
                    >
                      <i class="fa fa-bug judge-action-icon" aria-hidden="true"></i>
                      <span>{{ $t('m.Online_Test') }}</span>
                    </el-button>
                    <el-button
                      type="primary"
                      :icon="legacyElementIcons['el-icon-edit-outline']"
                      :loading="submitting"
                      @click="submitCode"
                      :disabled="problemSubmitDisabled || submitted || submitDisabled"
                      class="judge-action-button submit-judge-button"
                    >
                      <span v-if="submitting">{{ $t('m.Submitting') }}</span>
                      <span v-else>{{ $t('m.Submit') }}</span>
                    </el-button>
                  </div>
                </el-col>
              </el-row>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
    <ProblemHorizontalMenu
      v-if="showProblemHorizontalMenu"
      v-model:pid="problemData.problem.id"
      :cid="contestID"
      :tid="trainingID"
      ref="problemHorizontalMenu"
      :gid="groupID">
    </ProblemHorizontalMenu>

    <AiProblemAssistant
      v-if="aiAssistantAvailable"
      :key="`${$route.name}-${problemData.problem.id}`"
      :problem="problemData.problem"
      :code="code"
      :language="language"
      :source-type="aiAssistantSourceType"
      :training-id="trainingID"
    />

    <el-dialog
      v-model="graphVisible"
      width="400px"
      class="problem-statistics-dialog"
    >
      <div id="pieChart-detail">
        <ECharts
          v-if="graphVisible"
          :option="largePie"
          :init-options="largePieInitOpts"
        ></ECharts>
      </div>
      <template #footer>
        <div>
          <el-button
            type="ghost"
            @click="graphVisible = false"
            size="small"
          >{{
            $t('m.Close')
          }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="submitPwdVisible"
      width="340px"
    >
      <el-form>
        <el-form-item
          :label="$t('m.Enter_the_contest_password')"
          required
        >
          <el-input
            :placeholder="$t('m.Enter_the_contest_password')"
            v-model="submitPwd"
            show-password
          ></el-input>
        </el-form-item>
        <el-button
          type="primary"
          round
          style="margin-left:130px"
          @click="checkContestPassword"
        >
          {{ $t('m.Submit') }}
        </el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters, mapActions } from "vuex";
import storage from "@/common/storage";
import utils from "@/common/utils";
import {
  JUDGE_STATUS,
  CONTEST_STATUS,
  JUDGE_STATUS_RESERVE,
  buildProblemCodeAndSettingKey,
  buildIndividualLanguageAndSettingKey,
  RULE_TYPE,
  PROBLEM_LEVEL,
} from "@/common/constants";
import { pie, largePie } from "./chartData";
import api from "@/common/api";
import myMessage from "@/common/message";
import { addCodeBtn } from "@/common/codeblock";
import CodeMirror from "@/components/oj/common/CodeMirror.vue";
import Pagination from "@/components/oj/common/Pagination";
import ProblemHorizontalMenu from "@/components/oj/common/ProblemHorizontalMenu";
import Markdown from "@/components/oj/common/Markdown";
import AiProblemAssistant from "@/components/oj/problem/AiProblemAssistant.vue";
// 只显示这些状态的图形占用
const filtedStatus = ["wa", "ce", "ac", "pa", "tle", "mle", "re", "pe"];

export default {
  name: "ProblemDetails",
  components: {
    CodeMirror,
    Pagination,
    ProblemHorizontalMenu,
    Markdown,
    AiProblemAssistant
  },
  data() {
    return {
      statusVisible: false,
      captchaRequired: false,
      graphVisible: false,
      submissionExists: false,
      captchaCode: "",
      captchaSrc: "",
      contestID: 0,
      groupID: null,
      problemID: "",
      trainingID: null,
      submitting: false,
      code: "",
      language: "",
      isRemote: false,
      theme: "solarized",
      fontSize: "14px",
      tabSize: 4,
      height: 550,
      submissionId: "",
      submitted: false,
      submitDisabled: false,
      submitPwdVisible: false,
      submitPwd: "",
      result: {
        status: 9,
      },
      problemData: {
        problem: {
          difficulty: 0,
        },
        problemCount: {},
        tags: [],
        languages: [],
        codeTemplate: {},
      },
      pie: pie,
      largePie: largePie,
      // 线上原版使用 380 × 380 的画布绘制在 350px 的图表容器中。
      largePieInitOpts: {
        width: 380,
        height: 380,
      },
      JUDGE_STATUS_RESERVE: {},
      JUDGE_STATUS: {},
      PROBLEM_LEVEL,
      RULE_TYPE: {},
      toResetWatch: false,
      toWatchProblem: false,
      activeName: "problemDetail",
      loadingTable: false,
      mySubmission_total: 0,
      mySubmission_limit: 10,
      mySubmission_currentPage: 1,
      mySubmissions: [],
      loading: false,
      bodyClass: "",
      userExtraFile: null,
      fileContent: "",
      fileName: "",
      openTestCaseDrawer: false,
      openFocusMode: false,
      showProblemHorizontalMenu: false,
    };
  },
  created() {
    this.initProblemCodeAndSetting();
    this.restoreActiveTab();
    this.JUDGE_STATUS_RESERVE = Object.assign({}, JUDGE_STATUS_RESERVE);
    this.JUDGE_STATUS = Object.assign({}, JUDGE_STATUS);
    this.RULE_TYPE = Object.assign({}, RULE_TYPE);
    let isFocusModePage = utils.isFocusModePage(this.$route.name);
    if (
      this.$route.name === "ProblemDetails" || isFocusModePage
    ) {
      this.bodyClass = "problem-body";
    }
    if(isFocusModePage && (this.$route.params.contestID || this.$route.params.trainingID)){
      this.contestID = this.$route.params.contestID;
      this.trainingID = this.$route.params.trainingID;
      this.showProblemHorizontalMenu = true;
    }
  },

  mounted() {
    this.init();
    this.resizeWatchHeight();
    window.onresize = () => {
      this.resizeWatchHeight();
    };
  },
  methods: {
    ...mapActions(["changeDomTitle"]),
    activeTabStorageKey() {
      const route = this.$route;
      return [
        "hoj-problem-active-tab",
        route.name || "",
        route.params.problemID || "",
        route.params.contestID || "",
        route.params.trainingID || "",
        route.params.groupID || "",
      ].join(":");
    },
    rememberActiveTabForReturn() {
      window.sessionStorage.setItem(
        this.activeTabStorageKey(),
        this.activeName
      );
    },
    restoreActiveTab() {
      const key = this.activeTabStorageKey();
      const savedTab = window.sessionStorage.getItem(key);
      window.sessionStorage.removeItem(key);
      if (savedTab === "problemDetail" || savedTab === "mySubmission") {
        this.activeName = savedTab;
      }
    },
    initProblemCodeAndSetting() {
      this.code = "";
      // 获取缓存中的该题的做题代码，代码语言，代码风格
      let problemCodeAndSetting = storage.get(
        buildProblemCodeAndSettingKey(
          this.$route.params.problemID,
          this.$route.params.contestID
        )
      );
      if (problemCodeAndSetting) {
        this.language = problemCodeAndSetting.language;
        this.code = problemCodeAndSetting.code;
        this.theme = problemCodeAndSetting.theme;
        this.fontSize = problemCodeAndSetting.fontSize;
        this.tabSize = problemCodeAndSetting.tabSize;
      } else {
        let individualLanguageAndSetting = storage.get(
          buildIndividualLanguageAndSettingKey()
        );
        if (individualLanguageAndSetting) {
          this.language = individualLanguageAndSetting.language;
          this.theme = individualLanguageAndSetting.theme;
          this.fontSize = individualLanguageAndSetting.fontSize;
          this.tabSize = individualLanguageAndSetting.tabSize;
        }
      }
    },
    handleClickTab({ paneName }) {
      if (paneName == "mySubmission" && this.isAuthenticated) {
        this.getMySubmission();
      }
    },
    getMySubmission() {
      let params = {
        onlyMine: true,
        currentPage: this.mySubmission_currentPage,
        problemID: this.problemID,
        contestID: this.contestID,
        completeProblemID: true,
        gid: this.groupID,
        limit: this.mySubmission_limit,
      };
      if (this.contestID) {
        if (this.contestStatus == CONTEST_STATUS.SCHEDULED) {
          params.beforeContestSubmit = true;
        } else {
          params.beforeContestSubmit = false;
        }
        params.containsEnd = true;
      }
      let func = this.contestID
        ? "getContestSubmissionList"
        : "getSubmissionList";
      this.loadingTable = true;
      api[func](this.mySubmission_limit, utils.filterEmptyValue(params))
        .then(
          (res) => {
            let data = res.data.data;
            this.mySubmissions = data.records;
            this.mySubmission_total = data.total;
            this.loadingTable = false;
          },
          (err) => {
            this.loadingTable = false;
          }
        )
        .catch(() => {
          this.loadingTable = false;
        });
    },
    getStatusColor(status) {
      return "el-tag el-tag--medium status-" + JUDGE_STATUS[status].color;
    },
    submissionTimeFormat(time) {
      return utils.submissionTimeFormat(time);
    },

    submissionMemoryFormat(memory) {
      return utils.submissionMemoryFormat(memory);
    },

    showSubmitDetail(row) {
      this.rememberActiveTabForReturn();
      if (row.cid != 0) {
        // 比赛提交详情
        this.$router.push({
          name: "ContestSubmissionDetails",
          params: {
            contestID: this.$route.params.contestID,
            problemID: row.displayId,
            submitID: row.submitId,
          },
        });
      } else if (this.groupID) {
        this.$router.push({
          name: "GroupSubmissionDetails",
          params: {
            submitID: row.submitId,
          },
        });
      } else {
        this.$router.push({
          name: "SubmissionDetails",
          params: { submitID: row.submitId },
        });
      }
    },

    startResize(event) {
      if (event.button !== 0) {
        return;
      }

      const resize = event.currentTarget;
      const left = document.getElementById(
        "problem-left" + "-" + this.$route.name
      );
      const right = document.getElementById(
        "problem-right" + "-" + this.$route.name
      );
      const box = document.getElementById(
        "problem-box" + "-" + this.$route.name
      );
      if (!resize || !left || !right || !box) {
        return;
      }

      const boxRect = box.getBoundingClientRect();
      const minLeftWidth = 420;
      const minRightWidth = 580;
      resize.classList.add("is-dragging");

      const onMouseMove = (moveEvent) => {
        const maxLeftWidth = Math.max(
          0,
          box.clientWidth - resize.offsetWidth - minRightWidth
        );
        const pointerLeft = moveEvent.clientX - boxRect.left;
        let leftWidth = Math.min(pointerLeft, maxLeftWidth);

        if (pointerLeft < minLeftWidth) {
          leftWidth = 0;
          this.toWatchProblem = true;
        } else {
          leftWidth = Math.max(minLeftWidth, leftWidth);
          this.toWatchProblem = false;
        }

        const leftRatio = (leftWidth / box.clientWidth) * 100;
        resize.style.left = leftRatio + "%";
        left.style.width = leftRatio + "%";
        right.style.width = 100 - leftRatio + "%";
        right.style.display = "";
        this.toResetWatch = false;
      };

      const onMouseUp = () => {
        resize.classList.remove("is-dragging");
        document.removeEventListener("mousemove", onMouseMove);
        document.removeEventListener("mouseup", onMouseUp);
        this.resizeWatchHeight();
      };

      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
      event.preventDefault();
    },
    onlyWatchProblem() {
      if (this.toWatchProblem) {
        this.resetWatch(true);
        this.toWatchProblem = false;
        return;
      }
      var resize = document.getElementById(
        "js-center" + "-" + this.$route.name
      );
      var left = document.getElementById(
        "problem-left" + "-" + this.$route.name
      );
      var right = document.getElementById(
        "problem-right" + "-" + this.$route.name
      );
      var box = document.getElementById(
        "problem-box" + "-" + this.$route.name
      );
      resize.style.left = box.clientWidth - 10 + "px";
      left.style.width = box.clientWidth - 10 + "px";
      right.style.width = "0px";
      right.style.display = "none";
      this.toResetWatch = true;
    },
    resetWatch(minLeft = false) {
      var resize = document.getElementById(
        "js-center" + "-" + this.$route.name
      );
      var left = document.getElementById(
        "problem-left" + "-" + this.$route.name
      );
      var right = document.getElementById(
        "problem-right" + "-" + this.$route.name
      );
      var box = document.getElementById(
        "problem-box" + "-" + this.$route.name
      );

      let leftWidth = 0;
      if (minLeft) {
        leftWidth = 431; // 恢复左边最小420px+滑块11px
      } else {
        leftWidth = box.clientWidth - 580; // 右边最小580px
      }
      let leftRadio = (leftWidth / box.offsetWidth) * 100;
      resize.style.left = leftRadio + "%";
      left.style.width = leftRadio + "%";
      right.style.width = (100 - leftRadio)  + "%";
      right.style.display = "";
      this.toResetWatch = false;
    },
    resizeWatchHeight() {
      try {
        let headerHeight = document.getElementById("header").offsetHeight;
        let headerWidth = document.getElementById("header").offsetWidth;
        let totalHeight = window.innerHeight;

        let left = document.getElementById(
            "problem-left" + "-" + this.$route.name
          );
        let right = document.getElementById(
            "problem-right" + "-" + this.$route.name
          );
        if(headerWidth >= 992){
          let box = document.getElementById(
            "problem-box" + "-" + this.$route.name
          );
          let tmp = (left.clientWidth / box.clientWidth) * 100;
          left.style.width = tmp + "%";
          right.style.width = (100 - tmp) + "%";
        }else{
          right.style.width = "100%";
        }

        let problemLeftHight = totalHeight - (headerHeight + 64);
        if(this.showProblemHorizontalMenu){
          let footerMenuHeight = document.getElementById("problem-footer").offsetHeight;
          problemLeftHight = problemLeftHight - footerMenuHeight;
        }
        let jsRHeaderHeight =
          document.getElementById("js-right-header").offsetHeight;
        let jsRBottomHeight =
          document.getElementById("js-right-bottom").offsetHeight;

        if (jsRBottomHeight < 48) {
          jsRBottomHeight = 48;
        }

        let problemRightHight = problemLeftHight - 95 - (jsRHeaderHeight - 36) - (jsRBottomHeight - 48);
        if (problemRightHight < 0) {
          problemRightHight = 0;
        }
        this.height = problemRightHight;
        if (problemLeftHight < 0) {
          problemLeftHight = 0;
        }
        if (this.activeName == "problemDetail") {
          if(headerWidth >= 992){
            document
            .getElementById("js-left" + "-" + this.$route.name)
            .setAttribute(
              "style",
              "height:" + problemLeftHight + "px !important"
            );
          }else{
            document
            .getElementById("js-left" + "-" + this.$route.name)
            .setAttribute(
              "style",
              "height: auto"
            );
          }
        } else if (this.activeName == "mySubmission") {
          document
            .getElementById("js-submission")
            .setAttribute(
              "style",
              "height:" + problemLeftHight + "px !important"
            );
        } else if (this.activeName == "extraFile") {
          document
            .getElementById("js-extraFile")
            .setAttribute(
              "style",
              "height:" + problemLeftHight + "px !important"
            );
        }
        document
          .getElementById("js-center" + "-" + this.$route.name)
          .setAttribute(
            "style",
            "top:" + problemLeftHight * 0.5 + "px !important; left:" 
            + left.style.width
          );
      } catch (e) {
      }
    },
    init() {
      if(this.$route.name === "ContestFullProblemDetails"){
        this.$store.dispatch('getContest');
      }
      this.openFocusMode = utils.isFocusModePage(this.$route.name);
      if (this.$route.params.contestID) {
        this.contestID = this.$route.params.contestID;
      }
      if (this.$route.params.groupID) {
        this.groupID = this.$route.params.groupID;
      }
      this.problemID = this.$route.params.problemID;
      if (this.$route.params.trainingID) {
        this.trainingID = this.$route.params.trainingID;
      }
      let func =
        this.$route.name === "ContestProblemDetails" ||
        this.$route.name === "ContestFullProblemDetails"
          ? "getContestProblem"
          : "getProblem";
      this.loading = true;
      api[func](this.problemID, this.contestID, this.groupID, true).then(
        (res) => {
          let result = res.data.data;
          this.changeDomTitle({ title: result.problem.title });
          result["myStatus"] = -10; // 设置默认值

          result.problem.examples = utils.stringToExamples(
            result.problem.examples
          );
          if (result.problem.userExtraFile) {
            this.userExtraFile = JSON.parse(result.problem.userExtraFile);
          }

          this.problemData = result;

          this.loading = false;

          if (this.isAuthenticated) {
            let pidList = [result.problem.id];
            let isContestProblemList = this.contestID ? true : false;
            api
              .getUserProblemStatus(
                pidList,
                isContestProblemList,
                this.contestID,
                this.groupID,
                true
              )
              .then((res) => {
                let statusMap = res.data.data;
                if (statusMap[result.problem.id].status != -10) {
                  this.submissionExists = true;
                  this.problemData.myStatus =
                    statusMap[result.problem.id].status;
                } else {
                  this.submissionExists = false;
                }
              });
          }

          this.isRemote = result.problem.isRemote;
          this.changePie(result.problemCount);

          // 在beforeRouteEnter中修改了, 说明本地有code，无需加载template
          if (this.code !== "") {
            return;
          }
          if (this.problemData.languages.length != 0) {
            if (
              !this.language ||
              this.problemData.languages.indexOf(this.language) == -1
            ) {
              this.language = this.problemData.languages[0];
            }
          }
          // try to load problem template
          let codeTemplate = this.problemData.codeTemplate;
          if (codeTemplate && codeTemplate[this.language]) {
            this.code = codeTemplate[this.language];
          }
          this.$nextTick((_) => {
            addCodeBtn();
          });
        },
        (err) => {
          this.submitDisabled = true;
          this.loading = false;
        }
      );
      
      if(this.activeName == "mySubmission"){
        this.getMySubmission();
      }
    },
    changePie(problemData) {
      let total = problemData.total;
      let acNum = problemData.ac;
      // 该状态结果数为0的不显示,同时一些无关参数也排除
      for (let k in problemData) {
        if (problemData[k] == 0 || filtedStatus.indexOf(k) === -1) {
          delete problemData[k];
        }
      }

      let data = [
        { name: "WA", value: total - acNum },
        { name: "AC", value: acNum },
      ];
      this.pie.series[0].data = data;
      // 只把大图的AC selected下，这里需要做一下deepcopy
      let data2 = JSON.parse(JSON.stringify(data));
      data2[1].selected = true;
      this.largePie.series[1].data = data2;

      // 根据结果设置legend,没有提交过的legend不显示
      let legend = Object.keys(problemData).map((ele) =>
        (ele + "").toUpperCase()
      );
      if (legend.length === 0) {
        legend.push("AC", "WA");
      }
      this.largePie.legend.data = legend;

      // 把ac的数据提取出来放在最后
      let acCount = problemData.ac;
      delete problemData.ac;

      let largePieData = [];
      Object.keys(problemData).forEach((ele) => {
        largePieData.push({
          name: (ele + "").toUpperCase(),
          value: problemData[ele],
        });
      });
      largePieData.push({ name: "AC", value: acCount });
      this.largePie.series[0].data = largePieData;
    },

    goProblemSubmission() {
      if (this.contestID) {
        this.$router.push({
          name: "ContestSubmissionList",
          params: { contestID: this.contestID },
          query: { problemID: this.problemID, completeProblemID: true },
        });
      } else if (this.groupID) {
        this.$router.push({
          name: "GroupSubmissionList",
          query: {
            problemID: this.problemID,
            completeProblemID: true,
            gid: this.groupID,
          },
        });
      } else {
        this.$router.push({
          name: "SubmissionList",
          query: {
            problemID: this.problemID,
            completeProblemID: true,
          },
        });
      }
    },
    goProblemDiscussion() {
      if (this.groupID) {
        this.$router.push({
          name: "GroupProblemDiscussion",
          params: { problemID: this.problemID, groupID: this.groupID },
        });
      } else {
        this.$router.push({
          name: "ProblemDiscussion",
          params: { problemID: this.problemID },
        });
      }
    },

    onChangeLang(newLang) {
      if (this.code == this.problemData.codeTemplate[this.language]) {
        //原语言模板未变化，只改变语言
        if (this.problemData.codeTemplate[newLang]) {
          this.code = this.problemData.codeTemplate[newLang];
        } else {
          this.code = "";
        }
      }
      this.language = newLang;
    },
    onChangeTheme(newTheme) {
      this.theme = newTheme;
    },
    onResetToTemplate() {
      this.$confirm(
        this.$t("m.Are_you_sure_you_want_to_reset_your_code"),
        "Tips",
        {
          cancelButtonText: this.$t("m.Cancel"),
          confirmButtonText: this.$t("m.OK"),
          type: "warning",
        }
      )
        .then(() => {
          let codeTemplate = this.problemData.codeTemplate;
          if (codeTemplate && codeTemplate[this.language]) {
            this.code = codeTemplate[this.language];
          } else {
            this.code = "";
          }
        })
        .catch(() => {});
    },
    getUserLastAccepetedCode() {
      if (this.problemData.myStatus != 0) {
        this.$notify.error({
          title: this.$t("m.Error"),
          message: this.$t(
            "m.You_havenot_passed_the_problem_so_you_cannot_get_the_code_passed_recently"
          ),
          duration: 4000,
          offset: 50,
        });
        return;
      }
      this.$confirm(
        this.$t(
          "m.Are_you_sure_you_want_to_get_your_recent_accepted_code"
        ),
        "Tips",
        {
          cancelButtonText: this.$t("m.Cancel"),
          confirmButtonText: this.$t("m.OK"),
          type: "warning",
        }
      )
        .then(() => {
          api
            .getUserLastAccepetedCode(
              this.problemData.problem.id,
              this.contestID
            )
            .then((res) => {
              this.code = res.data.data.code;
              let lang = res.data.data.language;
              if (lang && this.problemData.languages.includes(lang)) {
                this.language = lang;
              }
            });
        })
        .catch(() => {});
    },
    checkSubmissionStatus() {
      // 使用setTimeout避免一些问题
      if (this.refreshStatus) {
        // 如果之前的提交状态检查还没有停止,则停止,否则将会失去timeout的引用造成无限请求
        clearTimeout(this.refreshStatus);
      }
      const checkStatus = () => {
        let submitId = this.submissionId;
        api.getSubmission(submitId).then(
          (res) => {
            this.result.status = res.data.data.submission.status;
            if (Object.keys(res.data.data.submission).length !== 0) {
              // status不为判题和排队中才表示此次判题结束
              if (
                res.data.data.submission.status !=
                  JUDGE_STATUS_RESERVE["Pending"] &&
                res.data.data.submission.status !=
                  JUDGE_STATUS_RESERVE["Compiling"] &&
                res.data.data.submission.status !=
                  JUDGE_STATUS_RESERVE["Judging"] &&
                res.data.data.submission.status !=
                  JUDGE_STATUS_RESERVE["Submitting"]
              ) {
                this.submitting = false;
                this.submitted = false;
                clearTimeout(this.refreshStatus);
                this.init();
                if(this.showProblemHorizontalMenu){
                  this.$refs.problemHorizontalMenu.getFullScreenProblemList();
                }
              } else {
                this.refreshStatus = setTimeout(checkStatus, 2000);
              }
            } else {
              this.refreshStatus = setTimeout(checkStatus, 2000);
            }
          },
          (res) => {
            this.submitting = false;
            this.submitted = false;
            clearTimeout(this.refreshStatus);
          }
        );
      };
      // 设置每2秒检查一下该题的提交结果
      this.refreshStatus = setTimeout(checkStatus, 2000);
    },

    checkContestPassword() {
      // 密码为空，需要重新输入
      if (!this.submitPwd) {
        myMessage.warning(this.$t("m.Enter_the_contest_password"));
        return;
      }
      api.registerContest(this.contestID + "", this.submitPwd).then(
        (res) => {
          this.$store.commit("contestSubmitAccess", { submitAccess: true });
          this.submitPwdVisible = false;
          this.submitCode();
        },
        (res) => {}
      );
    },

    submitCode() {
      if (this.code.trim() === "") {
        myMessage.error(this.$t("m.Code_can_not_be_empty"));
        return;
      }

      if (this.code.length > 65535) {
        myMessage.error(this.$t("m.Code_Length_can_not_exceed_65535"));
        return;
      }

      // 比赛题目需要检查是否有权限提交
      if (!this.canSubmit && this.$route.params.contestID) {
        this.submitPwdVisible = true;
        return;
      }

      this.submissionId = "";
      this.result = { status: 9 };
      this.submitting = true;
      let data = {
        pid: this.problemID, // 如果是比赛题目就为display_id
        language: this.language,
        code: this.code,
        cid: this.contestID,
        tid: this.trainingID,
        gid: this.groupID,
        isRemote: this.isRemote,
      };
      if (this.captchaRequired) {
        data.captcha = this.captchaCode;
      }
      const submitFunc = (data, detailsVisible) => {
        this.statusVisible = true;
        api.submitCode(data).then(
          (res) => {
            this.submissionId = res.data.data && res.data.data.submitId;
            // 定时检查状态
            this.submitting = false;
            this.submissionExists = true;
            if (!detailsVisible) {
              this.$Modal.success({
                title: "Success",
                content: this.$t("m.Submit_code_successfully"),
              });
              return;
            } else {
              myMessage.success(this.$t("m.Submit_code_successfully"));
            }
            // 更新store的可提交权限
            if (!this.canSubmit) {
              this.$store.commit("contestIntoAccess", { access: true });
            }
            this.submitted = true;
            this.checkSubmissionStatus();
          },
          (res) => {
            // this.getCaptchaSrc();
            // if (res.data.data.startsWith('Captcha is required')) {
            //   this.captchaRequired = true;
            // }
            this.submitting = false;
            this.statusVisible = false;
          }
        );
      };

      if (
        this.contestRuleType === RULE_TYPE.OI &&
        !this.ContestRealTimePermission
      ) {
        if (this.submissionExists) {
          this.$confirm(
            this.$t(
              "m.You_have_submission_in_this_problem_sure_to_cover_it"
            ),
            "Warning",
            {
              confirmButtonText: this.$t("m.OK"),
              cancelButtonText: this.$t("m.Cancel"),
              type: "warning",
            }
          )
            .then(() => {
              // 暂时解决对话框与后面提示对话框冲突的问题(否则一闪而过）
              setTimeout(() => {
                submitFunc(data, false);
              }, 1000);
            })
            .catch(() => {
              this.submitting = false;
            });
        } else {
          submitFunc(data, false);
        }
      } else {
        submitFunc(data, true);
      }
    },

    reSubmit(submitId) {
      this.result = { status: 9 };
      this.submitting = true;
      api.reSubmitRemoteJudge(submitId).then(
        (res) => {
          myMessage.success(this.$t("m.Resubmitted_Successfully"));
          this.submitted = true;
          this.checkSubmissionStatus();
        },
        (err) => {
          this.submitting = false;
          this.statusVisible = false;
        }
      );
    },

    showExtraFileContent(name, content) {
      this.fileName = name;
      this.fileContent = content;
      this.$nextTick((_) => {
        addCodeBtn();
      });
    },
    downloadExtraFile() {
      utils.downloadFileByText(this.fileName, this.fileContent);
    },

    getLevelColor(difficulty) {
      return utils.getLevelColor(difficulty);
    },
    getLevelName(difficulty) {
      return utils.getLevelName(difficulty);
    },
    goUserHome(username) {
      this.$router.push({
        path: "/user-home",
        query: { username },
      });
    },
    calcOIRankScore(score, difficulty) {
      return Math.round(0.1 * score + 2 * difficulty);
    },

    onCopy(event) {
      myMessage.success(this.$t("m.Copied_successfully"));
    },
    onCopyError(e) {
      myMessage.success(this.$t("m.Copied_failed"));
    },
    submissionRoute() {
      if (
        !this.submissionId ||
        this.result.status == JUDGE_STATUS_RESERVE["Pending"] ||
        this.result.status == JUDGE_STATUS_RESERVE["Compiling"] ||
        this.result.status == JUDGE_STATUS_RESERVE["Judging"] ||
        this.result.status == JUDGE_STATUS_RESERVE["Submitting"]
      ) {
        return;
      }
      this.rememberActiveTabForReturn();
      if (this.contestID) {
        this.$router.push({
          name: "ContestSubmissionDetails",
          params: {
            contestID: this.contestID,
            problemID: this.problemID,
            submitID: this.submissionId,
          },
        });
      } else if (this.groupID) {
        this.$router.push({
          name: "GroupSubmissionDetails",
          params: { submitID: this.submissionId, gid: this.groupID },
        });
      } else {
        this.$router.push({
          name: "SubmissionDetails",
          params: { submitID: this.submissionId },
        });
      }
    },
    openTestJudgeDrawer() {
      this.openTestCaseDrawer = !this.openTestCaseDrawer;
    },
    switchFocusMode(isOpen) {
      this.openFocusMode = isOpen;
      this.$router.push({
        name: utils.getSwitchFoceusModeRouteName(this.$route.name),
        params: {
          trainingID: this.trainingID,
          contestID: this.contestID,
          problemID: this.problemID,
          groupID: this.groupID,
        },
      });
    },
    beforeLeaveDo(cid){
      clearInterval(this.refreshStatus);
      storage.set(
        buildProblemCodeAndSettingKey(this.problemID, cid),
        {
          code: this.code,
          language: this.language,
          theme: this.theme,
          fontSize: this.fontSize,
          tabSize: this.tabSize,
        }
      );

      storage.set(buildIndividualLanguageAndSettingKey(), {
        language: this.language,
        theme: this.theme,
        fontSize: this.fontSize,
        tabSize: this.tabSize,
      });
    }
  },
  computed: {
    ...mapGetters([
      "problemSubmitDisabled",
      "contestRuleType",
      "ContestRealTimePermission",
      "contestStatus",
      "isAuthenticated",
      "canSubmit",
      "websiteConfig"
    ]),
    contest() {
      return this.$store.state.contest.contest;
    },
    contestEnded() {
      return this.contestStatus === CONTEST_STATUS.ENDED;
    },
    submissionStatus() {
      return {
        text: JUDGE_STATUS[this.result.status]["name"],
        color: JUDGE_STATUS[this.result.status]["rgb"],
      };
    },
    isCFProblem() {
      if (
        this.problemID.indexOf("CF-") == 0 ||
        this.problemID.indexOf("GYM-") == 0
      ) {
        return true;
      } else {
        return false;
      }
    },
    isShowProblemDiscussion() {
      if (!this.contestID) {
        if (this.groupID) {
          if (this.websiteConfig.openGroupDiscussion) {
            return true;
          }
        } else {
          if (this.websiteConfig.openPublicDiscussion) {
            return true;
          }
        }
      }
      return false;
    },
    problemType() {
      if (this.contestID) {
        return "contest";
      } else if (this.groupID) {
        return "group";
      } else {
        return "public";
      }
    },
    aiAssistantAvailable() {
      if (!this.isAuthenticated || !this.problemData.problem.id) {
        return false;
      }
      if (
        this.$route.name === "ProblemDetails" &&
        Number(this.problemData.problem.auth) !== 1
      ) {
        return false;
      }
      return [
        "ProblemDetails",
        "TrainingProblemDetails",
        "TrainingFullProblemDetails",
        "GroupTrainingProblemDetails",
        "GroupTrainingFullProblemDetails",
      ].includes(this.$route.name);
    },
    aiAssistantSourceType() {
      return [
        "TrainingProblemDetails",
        "TrainingFullProblemDetails",
        "GroupTrainingProblemDetails",
        "GroupTrainingFullProblemDetails",
      ].includes(this.$route.name)
        ? "TRAINING"
        : "PUBLIC";
    },
  },
  beforeRouteLeave(to, from, next) {
    // Vue Router may invoke this guard after the problem component has already
    // been unmounted (for example, when leaving a training problem via the
    // header tabs). In that short window `this` is null, so do not let saving
    // the editor state block the navigation itself.
    if (this?.beforeLeaveDo) {
      this.beforeLeaveDo(from.params.contestID);
    }
    if (this?.$route?.name === "ContestFullProblemDetails") {
      this.$store.commit('clearContest');
    }
    next();
  },
  beforeRouteUpdate (to, from, next) {
    if (this?.beforeLeaveDo) {
      this.beforeLeaveDo(from.params.contestID);
    }
    next();
  },
  watch: {
    $route() {
      this.initProblemCodeAndSetting();
      this.submitted = false;
      this.submitDisabled = false;
      this.submitting = false;
      this.statusVisible = false;
      this.init();
    },
    isAuthenticated(newVal) {
      if (newVal === true) {
        this.submitted = false;
        this.submitDisabled = false;
        this.submitting = false;
        this.statusVisible = false;
        this.init();
      }
    },
    activeName() {
      this.resizeWatchHeight();
    },
  },
};
</script>
<style>
.katex .katex-mathml {
  display: none;
}
</style>

<style scoped>
.problem-menu {
  float: left;
}
a {
  color: #3091f2 !important ;
}
.problem-menu span {
  margin-left: 5px;
}
.problem-menu-icon {
  margin-right: 4px;
}
.problem-difficulty-tag {
  height: 26px;
  padding: 0 8px;
}
.show-tags-trigger {
  height: 34px;
  padding: 0 11px;
}
.problem-topic-tag {
  height: 22px;
  padding: 0 8px;
}
.el-link {
  font-size: 16px !important;
}
.author-name {
  font-size: 14px !important;
  color: #909399 !important;
}
.question-intr {
  margin-top: 30px;
  border-radius: 4px;
  border: 1px solid #ddd;
  border-left: 2px solid #3498db;
  background: #fafafa;
  padding: 10px;
  line-height: 1.8;
  margin-bottom: 10px;
  font-size: 14px;
}

.extra-file {
  margin: 10px;
  cursor: pointer;
}
.file-download {
  vertical-align: bottom;
  float: right;
  margin-right: 5px;
}

.submit-detail {
  height: 100%;
}

:deep(.el-tabs--border-card > .el-tabs__content) {
  padding-top: 0px;
  padding-right: 0px;
  padding-bottom: 0px;
}

.js-left {
  padding-right: 15px;
}
@media screen and (min-width: 992px) {
  .problem-body {
    margin-left: -2% ;
    margin-right: -2%;
  }
  .js-left {
    height: 730px !important;
    overflow-y: auto;
  }
  #js-extraFile {
    overflow-y: auto;
  }
  #js-submission {
    overflow-y: auto;
  }
  .submit-detail {
    overflow-y: auto;
  }
  .js-right {
    height: 635px !important;
  }
  #js-right-bottom {
    height: 49px;
  }
  .problem-tag {
    display: inline;
  }
  .problem-menu {
    float: right;
  }
  .problem-menu span {
    margin-left: 10px;
  }
  .question-intr {
    margin-top: 6px;
  }
}

@media screen and (min-width: 992px) {
  .problem-box {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .problem-left {
    width: 50%; /*左侧初始化宽度*/
    flex: 0 0 auto;
    max-width: none;
    height: 100%;
    overflow-y: auto;
    overflow-x: hidden;
    float: left;
  }
  .problem-resize {
    cursor: col-resize;
    position: absolute;
    top: 330px;
    left: 50%;
    background-color: #d6d6d6;
    border-radius: 5px;
    width: 10px;
    height: 50px;
    z-index: 20;
    display: flex;
    align-items: center;
    justify-content: center;
    user-select: none;
    transition: background-color 0.15s ease;
  }
  .problem-resize.is-dragging {
    background-color: #818181;
  }
  .resize-grip {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    pointer-events: none;
  }
  .resize-grip i {
    display: block;
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background-color: #fff;
  }
  .problem-resize:hover .right-fold {
    display: block;
  }
  .problem-resize:hover .fold:before {
    content: "";
    position: absolute;
    display: block;
    width: 6px;
    height: 24px;
    left: -6px;
  }
  .right-fold {
    position: absolute;
    display: none;
    font-weight: bolder;
    margin-left: 15px;
    margin-top: -35px;
    cursor: pointer;
    z-index: 1000;
    text-align: center;
  }
  .left-fold {
    position: absolute;
    font-weight: bolder;
    margin-left: -40px;
    margin-top: 10px;
    cursor: pointer;
    z-index: 1000;
    text-align: center;
  }
  .fold:hover {
    color: #409eff;
    background: #fff;
  }

  /*拖拽区鼠标悬停样式*/
  .problem-resize:hover {
    color: #444444;
  }
  .problem-right {
    height: 100%;
    flex: 0 0 auto;
    max-width: none;
    float: left;
    width: 50%;
  }
}

@media screen and (max-width: 992px) {
  .submit-detail {
    padding-top: 20px;
  }
  .submit-detail {
    height: 100%;
  }
}
:deep(.el-card__header) {
  border-bottom: 0px;
  padding-bottom: 0px;
}
:deep(#submit-code > .el-card__body) {
  padding: 17px 20px 5px 24px !important;
}
#right-column {
  flex: none;
  width: 220px;
}

#problem-content {
  margin-top: -40px;
}
#problem-content .title {
  font-size: 16px;
  font-weight: 600;
  margin: 25px 0 8px 0;
  color: #3091f2;
}
#problem-content .copy {
  padding-left: 8px;
}

.hint-content {
  margin: 1em 0;
  font-size: 15px !important;
}

.md-content {
  margin: 1em;
  font-size: 15px;
}
.flex-container {
  display: flex;
  width: 100%;
  max-width: 100%;
  justify-content: space-around;
  align-items: flex-start;
  flex-flow: row nowrap;
}

.example {
  align-items: stretch;
}
.example-input,
.example-output {
  width: 50%;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}
.example pre {
  flex: 1 1 auto;
  align-self: stretch;
  border-style: solid;
  background: transparent;
  padding: 5px 10px;
  white-space: pre;
  margin-top: 10px;
  margin-bottom: 10px;
  background: #f1f1f1;
  border: 1px dashed #e9eaec;
  overflow: auto;
  font-size: 1.1em;
  margin-right: 7%;
}
#submit-code {
  height: auto;
}
#submit-code .status {
  display: flex;
  align-items: center;
  float: left;
  min-height: 30px;
  padding-left: 8px;
}
.submission-status {
  height: 30px;
  padding: 0 10px;
  font-size: 14px;
  line-height: 28px;
}
.submission-status i::before {
  margin-right: 4px;
}
.submission-status:hover {
  cursor: pointer;
}
#submit-code .status span {
  margin-left: 10px;
}
.captcha-container {
  display: inline-block;
}
.captcha-container .captcha-code {
  width: auto;
  margin-top: -20px;
  margin-left: 20px;
}

.fl-right {
  float: right;
}
:deep(.el-dialog__body) {
  padding: 10px 10px !important;
}
#pieChart .echarts {
  height: 250px;
  width: 210px;
}
#pieChart #detail {
  position: absolute;
  right: 10px;
  top: 10px;
}
#pieChart-detail {
  width: 380px;
  height: 350px;
  margin: 0;
}
#pieChart-detail :deep(.echarts) {
  width: 350px !important;
  height: 350px !important;
}
:deep(.problem-statistics-dialog) {
  padding: 0;
  margin: 15vh auto 50px !important;
  border-radius: 2px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 30%);
}
:deep(.problem-statistics-dialog .el-dialog__header) {
  box-sizing: border-box;
  width: 400px;
  height: 30px;
  margin: 0;
  padding: 20px 20px 10px;
}
:deep(.problem-statistics-dialog .el-dialog__headerbtn) {
  top: 20px;
  right: 20px;
  width: 16px;
  height: 24px;
  font-size: 16px;
  line-height: 24px;
}
:deep(.problem-statistics-dialog .el-dialog__body) {
  box-sizing: border-box;
  width: 400px;
  height: 370px;
  padding: 10px !important;
}
:deep(.problem-statistics-dialog .el-dialog__footer) {
  box-sizing: border-box;
  width: 400px;
  height: 62px;
  padding: 10px 20px 20px;
}
.judge-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 15px;
}
.judge-actions .el-button + .el-button {
  margin-left: 0;
}
.judge-action-button {
  height: 32px;
  border-radius: 4px;
  font-size: 12px;
}
.online-test-button {
  padding: 0 10px;
  border-color: #32ca99;
}
.online-test-button:hover,
.online-test-button:focus {
  background-color: #d5f1eb;
  border-color: #32ca99;
  color: #67c23a;
}
.online-test-button--active {
  background-color: #67c23a;
  border-color: #67c23a;
  color: #fff;
}
.online-test-button--active:hover,
.online-test-button--active:focus {
  background-color: #85ce61;
  border-color: #85ce61;
  color: #fff;
}
.submit-judge-button {
  padding: 0 15px;
}
.judge-action-icon {
  margin-right: 5px;
}
</style>
