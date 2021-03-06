/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.benchmark.test

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.gesture.pressIndicatorGestureFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.keyInputFilter
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.DisableTransitionsTestRule
import androidx.compose.ui.test.InternalTestingApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/**
 * Benchmark that sets the [LayoutNode.modifier].
 */
@LargeTest
@RunWith(Parameterized::class)
@OptIn(ExperimentalLayoutNodeApi::class)
class LayoutNodeModifierBenchmark(
    private val numberOfModifiers: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "modifiers={0}")
        fun initParameters(): Array<Any> = arrayOf(1, 5, 10)
    }

    @get:Rule
    val rule = SimpleAndroidBenchmarkRule()

    var modifiers = emptyList<Modifier>()
    var combinedModifier: Modifier = Modifier
    lateinit var layoutNode: LayoutNode

    @Before
    @OptIn(ExperimentalKeyInput::class)
    fun setup() {
        modifiers = listOf(
            Modifier.padding(10.dp),
            Modifier.drawBehind { },
            Modifier.graphicsLayer(),
            Modifier.keyInputFilter { _ -> true },
            Modifier.semantics { },
            Modifier.pressIndicatorGestureFilter(),
            Modifier.layoutId("Hello"),
            Modifier.padding(10.dp),
            Modifier.onGloballyPositioned { _ -> },
            Modifier.zIndex(1f)
        ).subList(0, numberOfModifiers)

        combinedModifier = modifiers.fold<Modifier, Modifier>(Modifier) { acc, modifier ->
            acc.then(modifier)
        }

        rule.activityTestRule.runOnUiThread {
            rule.activityTestRule.activity.setContent { Box(Modifier) }
        }
        rule.activityTestRule.runOnUiThread {
            val composeView = rule.findAndroidOwner()
            val root = composeView.root
            check(root.children.size == 1) { "Expecting only a Box" }
            layoutNode = root.children[0]
            check(layoutNode.children.isEmpty()) { "Box should be empty" }
        }
    }

    @Test
    fun setAndClearModifiers() {
        rule.activityTestRule.runOnUiThread {
            rule.benchmarkRule.measureRepeated {
                layoutNode.modifier = combinedModifier
                layoutNode.modifier = Modifier
            }
        }
    }

    @Test
    fun smallModifierChange() {
        rule.activityTestRule.runOnUiThread {
            val altModifier = Modifier.padding(10.dp).then(combinedModifier)
            layoutNode.modifier = altModifier

            rule.benchmarkRule.measureRepeated {
                layoutNode.modifier = combinedModifier
                layoutNode.modifier = altModifier
            }
        }
    }

    class SimpleAndroidBenchmarkRule() : TestRule {
        @Suppress("DEPRECATION")
        val activityTestRule =
            androidx.test.rule.ActivityTestRule(ComponentActivity::class.java)

        val benchmarkRule = BenchmarkRule()

        override fun apply(base: Statement, description: Description?): Statement {
            @OptIn(InternalTestingApi::class)
            return RuleChain
                .outerRule(DisableTransitionsTestRule())
                .around(benchmarkRule)
                .around(activityTestRule)
                .apply(base, description)
        }

        fun findAndroidOwner(): AndroidOwner {
            return activityTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as AndroidOwner
        }
    }
}
