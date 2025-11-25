import React from 'react';
import { DebitCard } from './components/debit-card';
import { CreditCard } from './components/credit-card';

export default function App() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-100 to-gray-200 py-12 px-4">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-12">
          <h1 className="text-black mb-4">Банковские карты</h1>
          <p className="text-gray-600">Премиальный дизайн дебетовых и кредитных карт</p>
        </div>
        
        <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
          <DebitCard />
          <CreditCard />
        </div>
      </div>
    </div>
  );
}
