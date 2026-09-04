package com.navpilot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.navpilot.core.ui.theme.Bg
import com.navpilot.presentation.components.BottomNav
import com.navpilot.presentation.navigation.AppScreen
import com.navpilot.presentation.screens.home.HomeScreen
import com.navpilot.presentation.screens.navigate.NavigateScreen
import com.navpilot.presentation.screens.offline.OfflineMapsScreen
import com.navpilot.presentation.screens.onboarding.OnboardingScreen
import com.navpilot.presentation.screens.settings.SettingsScreen
import com.navpilot.presentation.screens.setup.SetupScreen
import com.navpilot.presentation.screens.splash.SplashScreen
import com.navpilot.presentation.screens.tripdetail.TripDetailScreen
import com.navpilot.presentation.screens.trips.TripsScreen

@Composable
fun NavPilotApp() {
    var screen by remember {
        mutableStateOf(AppScreen.Splash)
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    val bottomNavigationScreens = setOf(
        AppScreen.Home,
        AppScreen.Navigate,
        AppScreen.Trips,
        AppScreen.Settings
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Bg
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (screen) {
                    AppScreen.Splash -> {
                        SplashScreen {
                            screen = AppScreen.Onboarding
                        }
                    }

                    AppScreen.Onboarding -> {
                        OnboardingScreen {
                            screen = AppScreen.Setup
                        }
                    }

                    AppScreen.Setup -> {
                        SetupScreen {
                            selectedTab = 0
                            screen = AppScreen.Home
                        }
                    }

                    AppScreen.Home -> {
                        HomeScreen { destination ->
                            when (destination) {
                                "offline" -> {
                                    screen = AppScreen.Offline
                                }

                                "navigate" -> {
                                    selectedTab = 1
                                    screen = AppScreen.Navigate
                                }

                                else -> {
                                    selectedTab = 1
                                    screen = AppScreen.Navigate
                                }
                            }
                        }
                    }

                    AppScreen.Navigate -> {
                        NavigateScreen()
                    }

                    AppScreen.Trips -> {
                        TripsScreen {
                            screen = AppScreen.TripDetail
                        }
                    }

                    AppScreen.TripDetail -> {
                        TripDetailScreen {
                            screen = AppScreen.Trips
                        }
                    }

                    AppScreen.Offline -> {
                        OfflineMapsScreen {
                            selectedTab = 3
                            screen = AppScreen.Settings
                        }
                    }

                    AppScreen.Settings -> {
                        SettingsScreen {
                            screen = AppScreen.Offline
                        }
                    }
                }
            }

            if (screen in bottomNavigationScreens) {
                BottomNav(
                    active = selectedTab,
                    onNavigate = { index ->
                        selectedTab = index

                        screen = when (index) {
                            0 -> AppScreen.Home
                            1 -> AppScreen.Navigate
                            2 -> AppScreen.Trips
                            else -> AppScreen.Settings
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )
            }
        }
    }
}
