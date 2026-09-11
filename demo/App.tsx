import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {ProductListScreen} from './src/screens/ProductListScreen';
import {CartScreen} from './src/screens/CartScreen';

export type RootStackParamList = {
  ProductList: undefined;
  Cart: {itemCount: number};
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App(): React.JSX.Element {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="ProductList">
        <Stack.Screen
          name="ProductList"
          component={ProductListScreen}
          options={{title: 'Storefront'}}
        />
        <Stack.Screen name="Cart" component={CartScreen} options={{title: 'Your Cart'}} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
