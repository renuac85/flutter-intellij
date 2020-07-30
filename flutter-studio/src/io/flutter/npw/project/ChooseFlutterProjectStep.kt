/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.npw.project

import com.android.tools.adtui.ASGallery
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep
import com.android.tools.idea.npw.template.getDefaultSelectedTemplateIndex
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.GuiUtils
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import io.flutter.FlutterBundle
import io.flutter.npw.model.NewFlutterProjectModel
import io.flutter.npw.model.RenderTemplateModel
import io.flutter.npw.template.ChooseGalleryItemStep
import io.flutter.npw.template.ConfigureTemplateParametersStep
import io.flutter.npw.template.Template
import io.flutter.npw.template.getTemplateIcon
import io.flutter.npw.template.getTemplateTitle
import io.flutter.wizard.template.FlutterWizardTemplateProvider
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

// See com.android.tools.idea.npw.project.ChooseAndroidProjectStep
class ChooseFlutterProjectStep(model: NewFlutterProjectModel) : ModelWizardStep<NewFlutterProjectModel>(
  model, FlutterBundle.message("studio.npw.select.template")
) {
  private var loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val tabsPanel = CommonTabbedPane()
  private val rootPanel = JPanel(GridLayoutManager(1, 1))
  private val canGoForward = BoolValueProperty()
  //private var newProjectModuleModel: NewProjectModuleModel

  init {
    loadingPanel.add(tabsPanel)

    val d = Dimension(-1, -1)
    val sp = GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK
    val gc = GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, sp, sp, d, d, d, 0, false)
    rootPanel.add(loadingPanel, gc)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    val renderModel = RenderTemplateModel.fromProjectModel(model)
    return listOf(ConfigureTemplateParametersStep(renderModel, FlutterBundle.message("studio.project.config.title"), listOf()))
  }

  private fun createUIComponents() {
    loadingPanel = object : JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)

        // Work-around for IDEA-205343 issue.
        components.forEach {
          it!!.setBounds(x, y, width, height)
        }
      }
    }
    loadingPanel.setLoadingText("Loading Android project template files")
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    loadingPanel.startLoading()
    // Unclear if a background thread is needed in this context.
    BackgroundTaskUtil.executeOnPooledThread(this, Runnable {

      // Update UI. Switch back to UI thread.
      GuiUtils.invokeLaterIfNeeded(
        { updateUi(wizard) },
        ModalityState.any())
    })
  }

  private fun updateUi(wizard: ModelWizard.Facade) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    FormScalingUtil.scaleComponentTree(this.javaClass, rootPanel)
    loadingPanel.stopLoading()
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun getComponent(): JComponent = rootPanel

  override fun getPreferredFocusComponent(): JComponent = tabsPanel

  interface TemplateRendererWithDescription : com.android.tools.idea.npw.template.ChooseGalleryItemStep.TemplateRenderer {
    val description: String
    val documentationUrl: String?
  }

  private class NewTemplateRendererWithDescription(
    template: Template
  ) : TemplateRendererWithDescription, ChooseGalleryItemStep.NewTemplateRenderer(template) {
    val t = template
    override val label: String get() = getTemplateTitle(t)
    override val icon: Icon? get() = getTemplateIcon(t)
    override val description: String get() = t.description
    override val documentationUrl: String? = t.documentationUrl
  }

  companion object {
    private fun getProjectTemplates() = FlutterWizardTemplateProvider().getTemplates()

    private fun createGallery(title: String): ASGallery<ChooseAndroidProjectStep.TemplateRendererWithDescription> {
      val listItems = sequence {
        getProjectTemplates().forEach { yield(NewTemplateRendererWithDescription(it)) }
      }.toList()

      return WizardGallery<ChooseAndroidProjectStep.TemplateRendererWithDescription>(title, { it!!.icon }, { it!!.label }).apply {
        model = JBList.createDefaultListModel(listItems)
        selectedIndex = getDefaultSelectedTemplateIndex(listItems)
      }
    }

  }
}