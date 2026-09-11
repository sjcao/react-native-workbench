import React, {useEffect, useState} from 'react';
import {FlatList, Pressable, StyleSheet, Text, View} from 'react-native';
import type {NativeStackScreenProps} from '@react-navigation/native-stack';
import type {RootStackParamList} from '../../App';

type Props = NativeStackScreenProps<RootStackParamList, 'ProductList'>;

interface Product {
  id: string;
  name: string;
  price: number;
}

const CATALOG: Product[] = [
  {id: 'p1', name: 'Wireless Earbuds', price: 59.99},
  {id: 'p2', name: 'Mechanical Keyboard', price: 89.0},
  {id: 'p3', name: 'USB-C Hub', price: 24.5},
];

export function ProductListScreen({navigation}: Props): React.JSX.Element {
  const [cartCount, setCartCount] = useState(0);

  useEffect(() => {
    navigation.setOptions({
      headerRight: () => <Text style={styles.badge}>{cartCount}</Text>,
    });
  }, [cartCount, navigation]);

  return (
    <View style={styles.container}>
      <FlatList
        data={CATALOG}
        keyExtractor={item => item.id}
        renderItem={({item}) => (
          <Pressable style={styles.row} onPress={() => setCartCount(c => c + 1)}>
            <Text style={styles.name}>{item.name}</Text>
            <Text style={styles.price}>${item.price.toFixed(2)}</Text>
          </Pressable>
        )}
      />
      <Pressable
        style={styles.checkoutButton}
        onPress={() => navigation.navigate('Cart', {itemCount: cartCount})}>
        <Text style={styles.checkoutText}>View Cart ({cartCount})</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#fff'},
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  name: {fontSize: 16},
  price: {fontSize: 16, fontWeight: '600'},
  badge: {marginRight: 12, fontWeight: 'bold'},
  checkoutButton: {backgroundColor: '#2563eb', padding: 16, alignItems: 'center'},
  checkoutText: {color: '#fff', fontWeight: '600'},
});
