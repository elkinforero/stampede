/*
 *     This file is part of ToroDB.
 *
 *     ToroDB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ToroDB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with ToroDB. If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright (c) 2014, 8Kdata Technology
 *     
 */

package com.torodb.di;

import com.eightkdata.mongowp.mongoserver.api.QueryCommandProcessor;
import com.eightkdata.mongowp.mongoserver.api.safe.*;
import com.eightkdata.mongowp.mongoserver.api.safe.impl.AtomicConnectionIdFactory;
import com.eightkdata.mongowp.mongoserver.callback.RequestProcessor;
import com.eightkdata.mongowp.mongoserver.pojos.OpTime;
import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.torodb.DefaultBuildProperties;
import com.torodb.Shutdowner;
import com.torodb.torod.core.BuildProperties;
import com.torodb.torod.mongodb.OptimeClock;
import com.torodb.torod.mongodb.ToroErrorHandler;
import com.torodb.torod.mongodb.annotations.External;
import com.torodb.torod.mongodb.annotations.Local;
import com.torodb.torod.mongodb.commands.ToroCommandsExecutorProvider;
import com.torodb.torod.mongodb.commands.ToroCommandsLibraryProvider;
import com.torodb.torod.mongodb.commands.WriteConcernToWriteFailModeFunction;
import com.torodb.torod.mongodb.commands.WriteConcernToWriteFailModeFunction.AlwaysTransactionalWriteFailMode;
import com.torodb.torod.mongodb.crp.Index;
import com.torodb.torod.mongodb.crp.MetaCollectionRequestProcessor;
import com.torodb.torod.mongodb.crp.Namespaces;
import com.torodb.torod.mongodb.impl.DefaultOpTimeClock;
import com.torodb.torod.mongodb.impl.LocalMongoClient;
import com.torodb.torod.mongodb.meta.IndexesMetaCollection;
import com.torodb.torod.mongodb.meta.NamespacesMetaCollection;
import com.torodb.torod.mongodb.repl.OplogManager;
import com.torodb.torod.mongodb.repl.OplogReaderProvider;
import com.torodb.torod.mongodb.repl.ReplCoordinator;
import com.torodb.torod.mongodb.repl.ReplCoordinator.ReplCoordinatorOwnerCallback;
import com.torodb.torod.mongodb.repl.SyncSourceProvider;
import com.torodb.torod.mongodb.repl.exceptions.NoSyncSourceFoundException;
import com.torodb.torod.mongodb.repl.impl.MongoOplogReaderProvider;
import com.torodb.torod.mongodb.srp.DatabaseCheckSafeRequestProcessor;
import com.torodb.torod.mongodb.srp.DatabaseIgnoreSafeRequestProcessor;
import com.torodb.torod.mongodb.srp.ToroSafeRequestProcessor;
import com.torodb.torod.mongodb.translator.QueryCriteriaTranslator;
import com.torodb.torod.mongodb.unsafe.ToroQueryCommandProcessor;
import com.torodb.torod.mongodb.utils.DBCloner;
import com.torodb.torod.mongodb.utils.MongoClientProvider;
import com.torodb.torod.mongodb.utils.OplogOperationApplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 *
 */
public class MongoLayerModule extends AbstractModule {

    private final HostAndPort syncSource;

    /**
     *
     * @param syncSource the sync source this node is going to replicate from or
     *                   null if this node must run as primary
     */
    public MongoLayerModule(@Nullable HostAndPort syncSource) {
        this.syncSource = syncSource;
    }

    @Override
    protected void configure() {
        bind(CommandsLibrary.class)
                .toProvider(ToroCommandsLibraryProvider.class)
                .asEagerSingleton();

        bind(CommandsExecutor.class)
                .toProvider(ToroCommandsExecutorProvider.class)
                .asEagerSingleton();

        bind(ToroSafeRequestProcessor.class);
        bind(DatabaseIgnoreSafeRequestProcessor.class);
        bind(SafeRequestProcessor.class)
                .annotatedWith(Local.class)
                .to(DatabaseIgnoreSafeRequestProcessor.class);
        bind(DatabaseCheckSafeRequestProcessor.class);
        bind(SafeRequestProcessor.class)
                .annotatedWith(External.class)
                .to(DatabaseCheckSafeRequestProcessor.class);

        bind(ConnectionIdFactory.class).to(AtomicConnectionIdFactory.class).in(Singleton.class);
        bind(ErrorHandler.class).to(ToroErrorHandler.class).in(Singleton.class);
        bind(BuildProperties.class).to(DefaultBuildProperties.class).asEagerSingleton();
        bind(QueryCommandProcessor.class).to(ToroQueryCommandProcessor.class);
        bind(QueryCriteriaTranslator.class).toInstance(new QueryCriteriaTranslator());

        bind(OptimeClock.class).to(DefaultOpTimeClock.class).in(Singleton.class);

        bind(OplogReaderProvider.class).to(MongoOplogReaderProvider.class).asEagerSingleton();
        bind(OplogOperationApplier.class);
        bind(LocalMongoClient.class);
        bind(DBCloner.class);
        bind(MongoClientProvider.class);

        bind(ReplCoordinator.class);

        bind(WriteConcernToWriteFailModeFunction.class).to(AlwaysTransactionalWriteFailMode.class);

        bind(Shutdowner.class).in(Singleton.class);
        bind(ReplCoordinatorOwnerCallback.class).to(Shutdowner.class);

        bind(OplogManager.class);
    }

    @Provides @Singleton
    RequestProcessor createRequestProcessor(
            ConnectionIdFactory connectionIdFactory,
            @External SafeRequestProcessor safeRequestProcessor,
            ErrorHandler errorHandler) {
        return new RequestProcessorAdaptor(connectionIdFactory, safeRequestProcessor, errorHandler);
    }

    @Provides @Singleton
    SyncSourceProvider createSyncSourceProvider() {
        if (syncSource != null) {
            return new FollowerSyncSourceProvider(syncSource);
        }
        else {
            return new PrimarySyncSourceProvider();
        }
    }

    @Provides @Index @Singleton
    MetaCollectionRequestProcessor createIndexMetaCollectionRequestProcessor(
            IndexesMetaCollection indexMetaCollection,
            QueryCriteriaTranslator qct,
            OptimeClock optimeClock) {
        return new MetaCollectionRequestProcessor(indexMetaCollection, qct, optimeClock);
    }

    @Provides @Namespaces @Singleton
    MetaCollectionRequestProcessor createNamespacesMetaCollectionRequestProcessor(
            NamespacesMetaCollection indexMetaCollection,
            QueryCriteriaTranslator qct,
            OptimeClock optimeClock) {
        return new MetaCollectionRequestProcessor(indexMetaCollection, qct, optimeClock);
    }

    @Beta
    private static class FollowerSyncSourceProvider implements SyncSourceProvider {
        private final HostAndPort syncSource;

        public FollowerSyncSourceProvider(@Nonnull HostAndPort syncSource) {
            this.syncSource = syncSource;
        }

        @Override
        public HostAndPort calculateSyncSource(HostAndPort oldSyncSource) {
            return syncSource;
        }

        @Override
        public HostAndPort getLastUsedSyncSource() {
            return syncSource;
        }

        @Override
        public HostAndPort getSyncSource(OpTime lastFetchedOpTime) throws
                NoSyncSourceFoundException {
            return syncSource;
        }
    }

    private static class PrimarySyncSourceProvider implements SyncSourceProvider {

        @Override
        public HostAndPort calculateSyncSource(HostAndPort oldSyncSource) throws
                NoSyncSourceFoundException {
            throw new NoSyncSourceFoundException();
        }

        @Override
        public HostAndPort getSyncSource(OpTime lastFetchedOpTime) throws
                NoSyncSourceFoundException {
            throw new NoSyncSourceFoundException();
        }

        @Override
        public HostAndPort getLastUsedSyncSource() {
            return null;
        }

    }

}
